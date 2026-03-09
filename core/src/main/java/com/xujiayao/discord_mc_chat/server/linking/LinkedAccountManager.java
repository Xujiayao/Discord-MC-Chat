package com.xujiayao.discord_mc_chat.server.linking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the persistent storage of Discord-to-Minecraft account links.
 * <p>
 * One Discord account can link multiple Minecraft accounts (1:N).
 * One Minecraft account can only link to one Discord account (N:1 uniqueness).
 * Data is stored in {@code linked_accounts.json} in the DMCC config directory.
 *
 * @author Xujiayao
 */
public class LinkedAccountManager {

	private static final Path LINKED_ACCOUNTS_FILE = Paths.get("./config/discord_mc_chat/linked_accounts.json");

	private static final ConcurrentHashMap<String, List<LinkEntry>> LINKED_ACCOUNTS = new ConcurrentHashMap<>();

	// Reverse index: Minecraft UUID -> Discord ID for O(1) lookups
	private static final ConcurrentHashMap<String, String> UUID_TO_DISCORD = new ConcurrentHashMap<>();

	/**
	 * A linked Minecraft account entry.
	 *
	 * @param minecraftUuid The UUID of the linked Minecraft account.
	 * @param linkedAt      The timestamp (epoch millis) when the link was created.
	 */
	public record LinkEntry(String minecraftUuid, long linkedAt) {
	}

	/**
	 * Loads linked accounts from the JSON file.
	 * Creates an empty file if it does not exist.
	 *
	 * @return true if the accounts were loaded successfully, false otherwise.
	 */
	public static boolean load() {
		try {
			Files.createDirectories(LINKED_ACCOUNTS_FILE.getParent());

			if (!Files.exists(LINKED_ACCOUNTS_FILE) || Files.size(LINKED_ACCOUNTS_FILE) == 0) {
				LINKED_ACCOUNTS.clear();
				UUID_TO_DISCORD.clear();
				save();
				LOGGER.info(I18nManager.getDmccTranslation("linking.manager.loaded", 0));
				return true;
			}

			Map<String, List<LinkEntry>> loaded = JSON_MAPPER.readValue(
					Files.newBufferedReader(LINKED_ACCOUNTS_FILE),
					new TypeReference<>() {
					}
			);

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
	 */
	public static synchronized void save() {
		try {
			Files.createDirectories(LINKED_ACCOUNTS_FILE.getParent());
			JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(LINKED_ACCOUNTS_FILE.toFile(), LINKED_ACCOUNTS);
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.saved"));
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("linking.manager.save_failed"), e);
		}
	}

	/**
	 * Saves the current state and clears all linked accounts from memory.
	 */
	public static void shutdown() {
		save();
		LINKED_ACCOUNTS.clear();
		UUID_TO_DISCORD.clear();
	}

	/**
	 * Links a Minecraft account to a Discord account.
	 * Enforces uniqueness: a Minecraft UUID can only be linked to one Discord account.
	 *
	 * @param discordId     The Discord user ID.
	 * @param minecraftUuid The Minecraft account UUID.
	 * @return true if the link was created successfully, false if the Minecraft UUID is already linked.
	 */
	public static synchronized boolean linkAccount(String discordId, String minecraftUuid) {
		String existingDiscordId = UUID_TO_DISCORD.get(minecraftUuid);
		if (existingDiscordId != null) {
			LOGGER.warn(I18nManager.getDmccTranslation("linking.manager.uuid_already_linked", minecraftUuid, existingDiscordId));
			return false;
		}

		LINKED_ACCOUNTS.computeIfAbsent(discordId, k -> new ArrayList<>())
				.add(new LinkEntry(minecraftUuid, System.currentTimeMillis()));
		UUID_TO_DISCORD.put(minecraftUuid, discordId);

		save();
		LOGGER.info(I18nManager.getDmccTranslation("linking.manager.linked", discordId, minecraftUuid));
		return true;
	}

	/**
	 * Removes all Minecraft account links for a Discord user.
	 *
	 * @param discordId The Discord user ID.
	 * @return The number of Minecraft accounts that were unlinked.
	 */
	public static synchronized int unlinkByDiscordId(String discordId) {
		List<LinkEntry> removed = LINKED_ACCOUNTS.remove(discordId);
		int count = (removed != null) ? removed.size() : 0;

		if (count > 0) {
			removed.forEach(entry -> UUID_TO_DISCORD.remove(entry.minecraftUuid()));
			save();
			LOGGER.info(I18nManager.getDmccTranslation("linking.manager.unlinked_discord", count, discordId));
		}

		return count;
	}

	/**
	 * Removes a specific Minecraft UUID link from any Discord account.
	 *
	 * @param minecraftUuid The Minecraft account UUID to unlink.
	 * @return true if the UUID was found and removed, false otherwise.
	 */
	public static synchronized boolean unlinkByMinecraftUuid(String minecraftUuid) {
		String discordId = UUID_TO_DISCORD.remove(minecraftUuid);
		if (discordId == null) {
			return false;
		}

		List<LinkEntry> entries = LINKED_ACCOUNTS.get(discordId);
		if (entries != null) {
			entries.removeIf(link -> link.minecraftUuid().equals(minecraftUuid));
			if (entries.isEmpty()) {
				LINKED_ACCOUNTS.remove(discordId);
			}
		}

		save();
		LOGGER.info(I18nManager.getDmccTranslation("linking.manager.unlinked_minecraft", minecraftUuid, discordId));
		return true;
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
	 * @param minecraftUuid The Minecraft account UUID.
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
}
