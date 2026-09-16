package com.xujiayao.discord_mc_chat.server.linking;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.server.message.MinecraftMessageParser;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the persistent storage of Discord-to-Minecraft account links.
 * <p>
 * One Discord account can link multiple Minecraft accounts (1:N).
 * One Minecraft account can only link to one Discord account (N:1 uniqueness).
 * Data is stored in {@code account_linking/links.json} in the DMCC config directory.
 *
 * @author Xujiayao
 */
public final class LinkedAccountManager {

	private static final Path LINKS_FILE = Paths.get("./config/discord_mc_chat/account_linking/links.json");

	private static final ConcurrentHashMap<String, List<LinkEntry>> LINKED_ACCOUNTS = new ConcurrentHashMap<>();

	// Reverse index: Minecraft UUID -> Discord ID for O(1) lookups
	private static final ConcurrentHashMap<String, String> UUID_TO_DISCORD = new ConcurrentHashMap<>();

	// Discord name resolver, set by the server module to avoid circular dependencies
	private static Function<String, String> discordNameResolver;

	// File writes are serialized on this dedicated thread: save() is called from the single-threaded command
	// executor and from the Netty I/O thread, and neither may block on rewriting the whole file.
	private static ExecutorService saveExecutor;

	private LinkedAccountManager() {
	}

	/**
	 * Registers a function that resolves Discord user ID to username.
	 *
	 * @param resolver A function that takes Discord ID and returns the username (or the ID itself as fallback).
	 */
	public static void setDiscordNameResolver(Function<String, String> resolver) {
		discordNameResolver = resolver;
	}

	/**
	 * Resolves a Discord username from an ID using the registered resolver.
	 * Falls back to the raw ID if no resolver is registered.
	 *
	 * @param discordId The Discord user ID.
	 * @return The resolved username, or the raw ID.
	 */
	public static String resolveDiscordName(String discordId) {
		if (discordNameResolver != null) {
			return discordNameResolver.apply(discordId);
		}
		return discordId;
	}

	/**
	 * Loads linked accounts from the JSON file.
	 * Creates an empty file if it does not exist.
	 *
	 * @return true if the accounts were loaded successfully, false otherwise.
	 */
	public static boolean load() {
		try {
			Files.createDirectories(LINKS_FILE.getParent());

			// The mention alias table is derived from the linked accounts, so drop the cached copy on every
			// (re)load - this also covers a links.json edited by hand while DMCC is running.
			MinecraftMessageParser.invalidateMentionCache();

			if (!Files.exists(LINKS_FILE) || Files.size(LINKS_FILE) == 0) {
				LINKED_ACCOUNTS.clear();
				UUID_TO_DISCORD.clear();
				save();
				LOGGER.info(I18nManager.getDmccTranslation("linking.manager.loaded", 0));
				return true;
			}

			Map<String, List<LinkEntry>> loaded;
			try (var reader = Files.newBufferedReader(LINKS_FILE)) {
				loaded = JSON_MAPPER.readValue(
						reader,
						new TypeReference<>() {
						}
				);
			}

			LINKED_ACCOUNTS.clear();
			UUID_TO_DISCORD.clear();
			loaded.forEach((discordId, entries) -> {
				LINKED_ACCOUNTS.put(discordId, new ArrayList<>(entries));
				entries.forEach(entry -> UUID_TO_DISCORD.put(entry.minecraftUuid(), discordId));
			});

			int totalLinks = LINKED_ACCOUNTS.values().stream().mapToInt(List::size).sum();
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.loaded", totalLinks));
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("linking.manager.load_failed"), e);
			return false;
		}
	}

	/**
	 * Saves the current linked accounts state to the JSON file.
	 * <p>
	 * The snapshot is taken synchronously, so the file always ends up containing the state the caller just
	 * produced, but it is written on a dedicated single-thread executor: the callers (the command executor
	 * and the Netty I/O thread) must not block on rewriting the whole file. The write goes to a temporary
	 * file in the same directory which is then moved into place, so an interrupted write can never leave a
	 * truncated {@code links.json} behind.
	 */
	public static synchronized void save() {
		Map<String, List<LinkEntry>> snapshot = new LinkedHashMap<>();
		LINKED_ACCOUNTS.forEach((discordId, entries) -> snapshot.put(discordId, List.copyOf(entries)));

		saveExecutor().execute(() -> writeToDisk(snapshot));
	}

	private static synchronized ExecutorService saveExecutor() {
		if (saveExecutor == null || saveExecutor.isShutdown()) {
			saveExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-LinkSave"));
		}
		return saveExecutor;
	}

	/**
	 * Writes one state snapshot to {@link #LINKS_FILE}, replacing the file in a single move.
	 *
	 * @param snapshot The linked accounts state to persist.
	 */
	private static void writeToDisk(Map<String, List<LinkEntry>> snapshot) {
		Path tempFile = null;
		try {
			Files.createDirectories(LINKS_FILE.getParent());
			tempFile = Files.createTempFile(LINKS_FILE.getParent(), "links", ".tmp");
			JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), snapshot);
			restoreFilePermissions(tempFile);
			replaceLinksFile(tempFile);
			// The temporary file is deliberately not cleared here: a successful move removed it, and the copy
			// fallback still has to have it removed below.
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.saved"));
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("linking.manager.save_failed"), e);
		} finally {
			deleteQuietly(tempFile);
		}
	}

	/**
	 * Replaces {@link #LINKS_FILE} with a completely written temporary file.
	 * <p>
	 * The move is tried atomically first, so an interrupted replacement can never leave a truncated file
	 * behind. Windows refuses to replace a file that another process (or a concurrent read) currently has
	 * open, so a plain move and finally an in-place copy are kept as fallbacks: the previous implementation
	 * always wrote in place, and a link must not be lost just because something else is reading the file.
	 * The caller removes the temporary file when it is still there afterwards.
	 *
	 * @param tempFile The completely written temporary file.
	 * @throws IOException When none of the replacements was possible.
	 */
	private static void replaceLinksFile(Path tempFile) throws IOException {
		try {
			Files.move(tempFile, LINKS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return;
		} catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
			// Fall through to the non-atomic replacements below.
		}

		try {
			Files.move(tempFile, LINKS_FILE, StandardCopyOption.REPLACE_EXISTING);
			return;
		} catch (IOException e) {
			// Fall through to the in-place copy below.
		}

		Files.copy(tempFile, LINKS_FILE, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Gives the temporary file the permissions the file it replaces had.
	 * <p>
	 * {@link Files#createTempFile} creates owner-only files, while a directly written {@code links.json} used
	 * the process umask, so the permissions are copied over before the file becomes visible.
	 *
	 * @param tempFile The temporary file that is about to replace {@link #LINKS_FILE}.
	 */
	private static void restoreFilePermissions(Path tempFile) {
		try {
			Set<PosixFilePermission> permissions = Files.exists(LINKS_FILE)
					? Files.getPosixFilePermissions(LINKS_FILE)
					: PosixFilePermissions.fromString("rw-r--r--");
			Files.setPosixFilePermissions(tempFile, permissions);
		} catch (IOException | UnsupportedOperationException ignored) {
			// File systems without POSIX permissions (e.g. Windows) have nothing to restore.
		}
	}

	/**
	 * Deletes a leftover temporary file, ignoring failures.
	 *
	 * @param file The file to delete, or {@code null}.
	 */
	private static void deleteQuietly(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// A leftover temporary file is harmless and removed by the next successful write.
		}
	}

	/**
	 * Clears all linked accounts from memory without writing to disk.
	 * <p>
	 * This intentionally does NOT save to disk, so users can manually edit
	 * {@code links.json} while DMCC is running and reload to apply their changes.
	 * Any in-memory changes that were not yet persisted via {@link #save()} will be lost.
	 * In practice, all mutations (link/unlink) call {@link #save()} immediately,
	 * so no data is lost under normal operation. Writes that {@link #save()} already handed to its executor
	 * are completed before the in-memory state is dropped.
	 */
	public static void shutdown() {
		synchronized (LinkedAccountManager.class) {
			if (saveExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(saveExecutor);
				saveExecutor = null;
			}
		}

		LINKED_ACCOUNTS.clear();
		UUID_TO_DISCORD.clear();
		discordNameResolver = null;
	}

	/**
	 * Links a Minecraft account to a Discord account.
	 * Enforces uniqueness: a Minecraft UUID can only be linked to one Discord account.
	 *
	 * @param discordId     The Discord user ID.
	 * @param discordName   The Discord username (for logging only, not persisted).
	 * @param minecraftUuid The Minecraft account UUID.
	 * @param minecraftName The Minecraft player name (for logging only, not persisted).
	 * @return true if the link was created successfully, false if the Minecraft UUID is already linked.
	 */
	public static synchronized boolean linkAccount(String discordId, String discordName, String minecraftUuid, String minecraftName) {
		String existingDiscordId = UUID_TO_DISCORD.get(minecraftUuid);
		if (existingDiscordId != null) {
			String existingDiscordName = resolveDiscordName(existingDiscordId);
			LOGGER.warn(I18nManager.getDmccTranslation("linking.manager.uuid_already_linked", minecraftName, minecraftUuid, existingDiscordName, existingDiscordId));
			return false;
		}

		LINKED_ACCOUNTS.computeIfAbsent(discordId, _ -> new ArrayList<>())
				.add(new LinkEntry(minecraftUuid, System.currentTimeMillis(), isOfflineUuid(minecraftUuid) ? minecraftName : null));
		UUID_TO_DISCORD.put(minecraftUuid, discordId);

		LOGGER.info(I18nManager.getDmccTranslation("linking.manager.linked", discordName, discordId, minecraftName, minecraftUuid));
		save();
		OpSyncManager.syncAll();
		return true;
	}

	/**
	 * Removes all Minecraft account links for a Discord user.
	 *
	 * @param discordId   The Discord user ID.
	 * @param discordName The Discord username (for logging only).
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
	 * Removes a specific Minecraft UUID link from any Discord account.
	 *
	 * @param minecraftUuid The Minecraft account UUID to unlink.
	 * @param minecraftName The Minecraft player name (for logging only).
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

	/**
	 * Looks up the Discord user ID linked to a given Minecraft UUID.
	 *
	 * @param minecraftUuid The Minecraft account UUID.
	 * @return The Discord user ID, or null if the UUID is not linked.
	 */
	public static String getDiscordIdByMinecraftUuid(String minecraftUuid) {
		return UUID_TO_DISCORD.get(minecraftUuid);
	}

	/**
	 * Gets all Minecraft UUIDs linked to a Discord user.
	 *
	 * @param discordId The Discord user ID.
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

	/**
	 * Checks if a Minecraft UUID is linked to any Discord account.
	 *
	 * @return true if the UUID is linked, false otherwise.
	 */
	public static boolean isMinecraftUuidLinked(String minecraftUuid) {
		return UUID_TO_DISCORD.containsKey(minecraftUuid);
	}

	/**
	 * Gets all linked accounts as an unmodifiable map.
	 *
	 * @return A map of Discord IDs to their linked account entries.
	 */
	public static Map<String, List<LinkEntry>> getAllLinks() {
		return Collections.unmodifiableMap(LINKED_ACCOUNTS);
	}

	private static boolean isOfflineUuid(String uuidString) {
		try {
			return UUID.fromString(uuidString).version() == 3;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * A linked Minecraft account entry.
	 *
	 * @param minecraftUuid     The UUID of the linked Minecraft account.
	 * @param linkedAt          The timestamp (epoch millis) when the link was created.
	 * @param offlinePlayerName The player name stored at link time for offline-mode UUIDs only.
	 *                          {@code null} for online-mode players (resolvable via Mojang API).
	 */
	public record LinkEntry(
			String minecraftUuid,
			long linkedAt,
			String offlinePlayerName
	) {
	}
}
