package com.xujiayao.discord_mc_chat.server.linking;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
public final class LinkedAccountManager {

	private static final Path LINKS_FILE = Path.of("./config/discord_mc_chat/account_linking/links.json");

	private static final ConcurrentHashMap<String, List<LinkEntry>> LINKED_ACCOUNTS = new ConcurrentHashMap<>();

	// Reverse index: Minecraft UUID -> Discord ID for O(1) lookups
	private static final ConcurrentHashMap<String, String> UUID_TO_DISCORD = new ConcurrentHashMap<>();

	// Discord name resolver, set by the server module to avoid circular dependencies
	private static Function<String, String> discordNameResolver;

	// Serializes save() disk writes on one background thread so callers (including Netty event loop
	// threads) no longer block on IO. Guarded by the LinkedAccountManager class monitor.
	private static ExecutorService writeExecutor;

	// Set by save(), cleared by the writer: mutations close together collapse into one write of the newest state.
	private static volatile boolean dirty;

	private LinkedAccountManager() {
	}

	public static void setDiscordNameResolver(Function<String, String> resolver) {
		discordNameResolver = resolver;
	}

	/**
	 * Falls back to the raw ID if no resolver is registered.
	 */
	public static String resolveDiscordName(String discordId) {
		if (discordNameResolver != null) {
			return discordNameResolver.apply(discordId);
		}
		return discordId;
	}

	/**
	 * Loads linked accounts from JSON, creating an empty file if it does not exist.
	 */
	public static boolean load() {
		try {
			Files.createDirectories(LINKS_FILE.getParent());

			if (!Files.exists(LINKS_FILE) || Files.size(LINKS_FILE) == 0) {
				LINKED_ACCOUNTS.clear();
				UUID_TO_DISCORD.clear();
				writeNow();
				LOGGER.info(I18nManager.getDmccTranslation("linking.manager.loaded", 0));
				return true;
			}

			ParsedLinks parsed;
			try (var reader = Files.newBufferedReader(LINKS_FILE)) {
				parsed = parseLinks(JSON_MAPPER.readTree(reader));
			}

			LINKED_ACCOUNTS.clear();
			UUID_TO_DISCORD.clear();
			parsed.links().forEach((discordId, entries) -> {
				LINKED_ACCOUNTS.put(discordId, new ArrayList<>(entries));
				entries.forEach(entry -> UUID_TO_DISCORD.put(entry.minecraftUuid(), discordId));
			});

			if (parsed.migrated()) {
				// Rewrite legacy files once so the next start reads the current format.
				save();
			}

			int totalLinks = LINKED_ACCOUNTS.values().stream().mapToInt(List::size).sum();
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.loaded", totalLinks));
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("linking.manager.load_failed"), e);
			return false;
		}
	}

	/**
	 * Parses links.json. Files written by older versions had no {@code playerName} and only carried
	 * {@code offlinePlayerName} for offline-mode UUIDs; that name is migrated into {@code playerName} and
	 * the legacy key is dropped on the next write.
	 *
	 * @param root The parsed JSON document.
	 * @return The links and whether the file still contained legacy fields and should be rewritten.
	 */
	static ParsedLinks parseLinks(JsonNode root) {
		Map<String, List<LinkEntry>> links = new LinkedHashMap<>();
		boolean migrated = false;

		if (root == null || !root.isObject()) {
			return new ParsedLinks(links, false);
		}

		for (Map.Entry<String, JsonNode> account : root.properties()) {
			JsonNode entriesNode = account.getValue();
			if (entriesNode == null || !entriesNode.isArray()) {
				continue;
			}

			List<LinkEntry> entries = new ArrayList<>();
			for (JsonNode node : entriesNode) {
				String minecraftUuid = node.path("minecraftUuid").asString(null);
				if (minecraftUuid == null || minecraftUuid.isBlank()) {
					continue;
				}

				if (node.has("offlinePlayerName")) {
					// Both the legacy name and the now-redundant null value are cleaned up by the rewrite.
					migrated = true;
				}

				String playerName = node.path("playerName").asString(null);
				if (playerName == null || playerName.isBlank()) {
					String legacyName = node.path("offlinePlayerName").asString(null);
					if (legacyName != null && !legacyName.isBlank()) {
						playerName = legacyName;
					}
				}

				entries.add(new LinkEntry(
						minecraftUuid,
						(playerName == null || playerName.isBlank()) ? null : playerName,
						node.path("linkedAt").asLong()));
			}

			if (!entries.isEmpty()) {
				links.put(account.getKey(), entries);
			}
		}

		return new ParsedLinks(links, migrated);
	}

	/**
	 * @param links    The links parsed from disk.
	 * @param migrated Whether at least one legacy entry was migrated to the current format.
	 */
	record ParsedLinks(Map<String, List<LinkEntry>> links, boolean migrated) {
	}

	/**
	 * The write is handed to the background executor so callers - including Netty event loop threads -
	 * are not blocked on disk IO; consecutive mutations coalesce into one write of the newest state.
	 * The in-memory maps stay the authoritative source and are left untouched, exactly as before.
	 */
	public static synchronized void save() {
		dirty = true;

		try {
			getOrCreateWriteExecutor().execute(LinkedAccountManager::writeIfDirty);
		} catch (RejectedExecutionException e) {
			// The executor is already shut down (shutdown/reload race): write on the caller thread.
			writeIfDirty();
		}
	}

	private static void writeIfDirty() {
		if (!dirty) {
			return;
		}

		dirty = false;
		writeNow();
	}

	/**
	 * Serialization runs under the class monitor, just like the original {@code synchronized save()} did.
	 * That is required: the stored values are mutable lists, so a write concurrent with {@code linkAccount}
	 * /{@code unlink*} could serialize a list while it is being modified. Nothing here mutates the maps.
	 */
	private static void writeNow() {
		synchronized (LinkedAccountManager.class) {
			try {
				Files.createDirectories(LINKS_FILE.getParent());
				JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(LINKS_FILE.toFile(), LINKED_ACCOUNTS);
				LOGGER.info(I18nManager.getDmccTranslation("linking.manager.saved"));
			} catch (IOException e) {
				LOGGER.error(I18nManager.getDmccTranslation("linking.manager.save_failed"), e);
			}
		}
	}

	private static ExecutorService getOrCreateWriteExecutor() {
		synchronized (LinkedAccountManager.class) {
			if (writeExecutor == null || writeExecutor.isShutdown()) {
				writeExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-DataSave"));
			}
			return writeExecutor;
		}
	}

	/**
	 * Barrier: on a single-thread executor a no-op task only runs after all previously queued writes.
	 */
	private static void flushPendingWrites() {
		ExecutorService executor;
		synchronized (LinkedAccountManager.class) {
			executor = writeExecutor;
		}

		if (executor == null) {
			writeIfDirty();
			return;
		}

		try {
			executor.submit(() -> {
			}).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			writeIfDirty();
		} catch (Exception e) {
			writeIfDirty();
		}
	}

	/**
	 * Clears all linked accounts from memory without writing to disk, so users can manually edit
	 * {@code links.json} while DMCC is running and reload to apply their changes. That is intentional:
	 * every mutation calls {@link #save()} immediately, so nothing is lost on the normal path.
	 * <p>
	 * A write already requested by {@link #save()} is completed before the maps are cleared; without that
	 * flush the emptied maps below could be written instead.
	 */
	public static void shutdown() {
		flushPendingWrites();

		ExecutorService executor;
		synchronized (LinkedAccountManager.class) {
			executor = writeExecutor;
		}

		// shutdownAnExecutor blocks, so it must not run while holding the class monitor: a write
		// submitted concurrently would then wait for that monitor and never let the executor terminate.
		if (executor != null) {
			ExecutorServiceUtils.shutdownAnExecutor(executor);
			synchronized (LinkedAccountManager.class) {
				if (writeExecutor == executor) {
					writeExecutor = null;
				}
			}
		}

		LINKED_ACCOUNTS.clear();
		UUID_TO_DISCORD.clear();
		discordNameResolver = null;
	}

	/**
	 * A Minecraft UUID can only be linked to one Discord account, and a Discord account can only link
	 * up to account_linking.max_minecraft_accounts_per_discord_user accounts (0 = unlimited).
	 *
	 * @param discordName   For logging only, not persisted.
	 * @param minecraftName The player name at link time; persisted so nickname sync never depends on a
	 *                      Mojang lookup for a name that DMCC already knows.
	 * @return The outcome of the attempt; only {@link LinkResult#SUCCESS} records the link.
	 */
	public static synchronized LinkResult linkAccount(String discordId, String discordName, String minecraftUuid, String minecraftName) {
		String existingDiscordId = UUID_TO_DISCORD.get(minecraftUuid);
		if (existingDiscordId != null) {
			String existingDiscordName = resolveDiscordName(existingDiscordId);
			LOGGER.warn(I18nManager.getDmccTranslation("linking.manager.uuid_already_linked", minecraftName, minecraftUuid, existingDiscordName, existingDiscordId));
			return LinkResult.UUID_ALREADY_LINKED;
		}

		int limit = Math.max(0, ConfigManager.getInt("account_linking.max_minecraft_accounts_per_discord_user", 1));
		List<LinkEntry> existingEntries = LINKED_ACCOUNTS.get(discordId);
		if (limit > 0 && existingEntries != null && existingEntries.size() >= limit) {
			LOGGER.warn(I18nManager.getDmccTranslation("linking.manager.limit_reached", discordName, discordId, limit));
			return LinkResult.LIMIT_REACHED;
		}

		LINKED_ACCOUNTS.computeIfAbsent(discordId, _ -> new ArrayList<>())
				.add(new LinkEntry(minecraftUuid, minecraftName, System.currentTimeMillis()));
		UUID_TO_DISCORD.put(minecraftUuid, discordId);

		LOGGER.info(I18nManager.getDmccTranslation("linking.manager.linked", discordName, discordId, minecraftName, minecraftUuid));
		save();
		OpSyncManager.syncAll();
		return LinkResult.SUCCESS;
	}

	/**
	 * Updates the remembered in-game name of an existing link. Called on every join so the nickname
	 * template follows renames and survives a restart without another Mojang lookup.
	 *
	 * @param minecraftUuid The linked Minecraft UUID.
	 * @param playerName    The current player name reported by the DMCC client.
	 */
	public static synchronized void updatePlayerName(String minecraftUuid, String playerName) {
		if (minecraftUuid == null || playerName == null || playerName.isBlank()) {
			return;
		}

		String discordId = UUID_TO_DISCORD.get(minecraftUuid);
		if (discordId == null) {
			return;
		}

		List<LinkEntry> entries = LINKED_ACCOUNTS.get(discordId);
		if (entries == null) {
			return;
		}

		for (int i = 0; i < entries.size(); i++) {
			LinkEntry entry = entries.get(i);
			if (entry.minecraftUuid().equals(minecraftUuid)) {
				if (playerName.equals(entry.playerName())) {
					return;
				}

				entries.set(i, new LinkEntry(entry.minecraftUuid(), playerName, entry.linkedAt()));
				save();
				return;
			}
		}
	}

	/**
	 * @param discordName For logging only.
	 * @return The number of Minecraft accounts that were unlinked.
	 */
	public static synchronized int unlinkByDiscordId(String discordId, String discordName) {
		List<LinkEntry> removed = LINKED_ACCOUNTS.remove(discordId);
		int count = (removed != null) ? removed.size() : 0;

		if (count > 0) {
			removed.forEach(entry -> UUID_TO_DISCORD.remove(entry.minecraftUuid()));
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.unlinked_discord", count, discordName, discordId));
			save();
			OpSyncManager.syncAll();
		}

		return count;
	}

	/**
	 * @param minecraftName For logging only.
	 * @return The Discord user ID that was unlinked from, or null if the UUID was not linked.
	 */
	public static synchronized String unlinkByMinecraftUuid(String minecraftUuid, String minecraftName) {
		if (minecraftUuid == null) {
			return null;
		}

		String discordId = UUID_TO_DISCORD.remove(minecraftUuid);
		if (discordId == null) {
			return null;
		}

		List<LinkEntry> entries = LINKED_ACCOUNTS.get(discordId);
		if (entries != null) {
			entries.removeIf(link -> link.minecraftUuid().equals(minecraftUuid));
			if (entries.isEmpty()) {
				LINKED_ACCOUNTS.remove(discordId);
			}
		}

		String discordName = resolveDiscordName(discordId);
		LOGGER.info(I18nManager.getDmccTranslation("linking.manager.unlinked_minecraft", minecraftName, minecraftUuid, discordName, discordId));
		save();
		OpSyncManager.syncAll();
		return discordId;
	}

	public static String getDiscordIdByMinecraftUuid(String minecraftUuid) {
		return UUID_TO_DISCORD.get(minecraftUuid);
	}

	/**
	 * @return An unmodifiable list of linked Minecraft UUIDs, or an empty list if none.
	 */
	public static List<String> getMinecraftUuidsByDiscordId(String discordId) {
		List<LinkEntry> entries = LINKED_ACCOUNTS.get(discordId);

		if (entries == null || entries.isEmpty()) {
			return Collections.emptyList();
		}

		return entries.stream()
				.map(LinkEntry::minecraftUuid)
				.toList();
	}

	public static boolean isMinecraftUuidLinked(String minecraftUuid) {
		return UUID_TO_DISCORD.containsKey(minecraftUuid);
	}

	/**
	 * Returns a snapshot of the accounts currently linked to one Discord user.
	 *
	 * @return A copy of the linked account entries, or an empty list if none.
	 */
	public static List<LinkEntry> getLinkEntriesByDiscordId(String discordId) {
		List<LinkEntry> entries = LINKED_ACCOUNTS.get(discordId);

		if (entries == null || entries.isEmpty()) {
			return Collections.emptyList();
		}

		return List.copyOf(entries);
	}

	/**
	 * Returns a read-only view of the currently linked accounts. It is a live view and not a snapshot:
	 * it reflects later link/unlink operations, and its values are the internal lists themselves, so
	 * callers must not modify them and must expect the content to change while they iterate.
	 *
	 * @return An unmodifiable view of the internal map of Discord IDs to their linked account entries.
	 */
	public static Map<String, List<LinkEntry>> getAllLinks() {
		return Collections.unmodifiableMap(LINKED_ACCOUNTS);
	}

	/**
	 * Resolves the stored in-game name of a linked account. Entries written before the name was persisted
	 * (legacy v4 links that never joined since) fall back to a Mojang lookup.
	 *
	 * @param entry The linked account.
	 * @return The player name, or the raw UUID when it cannot be resolved.
	 */
	public static String resolvePlayerName(LinkEntry entry) {
		if (entry.playerName() != null && !entry.playerName().isBlank()) {
			return entry.playerName();
		}
		return MojangUtils.resolvePlayerName(entry.minecraftUuid(), null);
	}

	/**
	 * The outcome of a link attempt.
	 */
	public enum LinkResult {
		/**
		 * The Minecraft account was linked to the Discord user.
		 */
		SUCCESS,
		/**
		 * The Minecraft UUID is already linked to another Discord user.
		 */
		UUID_ALREADY_LINKED,
		/**
		 * The Discord user already reached
		 * account_linking.max_minecraft_accounts_per_discord_user.
		 */
		LIMIT_REACHED
	}

	/**
	 * One linked Minecraft account.
	 *
	 * @param minecraftUuid The Minecraft UUID.
	 * @param playerName    The last known in-game name, captured at link time and refreshed on every
	 *                      join. Nullable only for legacy entries migrated from files that never stored
	 *                      a name (the load-time migration fills it from the old offlinePlayerName field).
	 * @param linkedAt      Epoch millis of when the link was created.
	 */
	public record LinkEntry(
			String minecraftUuid,
			String playerName,
			long linkedAt
	) {
	}
}
