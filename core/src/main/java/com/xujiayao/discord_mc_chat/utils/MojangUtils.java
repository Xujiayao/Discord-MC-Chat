package com.xujiayao.discord_mc_chat.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;

/**
 * Utilities for resolving Minecraft player names from UUIDs.
 * <p>
 * Supports both online (Mojang API) and offline UUID formats.
 * Provides fallback to raw UUID display when resolution fails.
 * Results are cached in memory to avoid repeated network calls.
 *
 * @author Xujiayao
 */
public class MojangUtils {

	private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();

	/**
	 * Resolves a Minecraft player name from a UUID string.
	 * <p>
	 * If the UUID is an offline-mode UUID (version 3), the name cannot be resolved from Mojang
	 * and will fall back to the raw UUID. For online-mode UUIDs (version 4), the Mojang session
	 * server is queried. Network failures also fall back to the raw UUID.
	 *
	 * @param uuidString The UUID string (standard dashed format, e.g. "069a79f4-44e9-4726-a5be-fca90e38aaf5").
	 * @return The resolved player name, or the original UUID string if resolution fails.
	 */
	public static String resolvePlayerName(String uuidString) {
		String cached = NAME_CACHE.get(uuidString);
		if (cached != null) {
			return cached;
		}

		try {
			UUID uuid = UUID.fromString(uuidString);

			// Check if this is an offline-mode UUID (version 3)
			if (uuid.version() == 3) {
				// Offline UUIDs are generated from "OfflinePlayer:" + name
				// We cannot reverse this, so return the raw UUID
				return uuidString;
			}

			// Online UUID (version 4) - query Mojang
			String dashlessUuid = uuidString.replace("-", "");
			String response = HttpUtils.get(PROFILE_URL + dashlessUuid);
			JsonNode profile = JSON_MAPPER.readTree(response);

			String name = profile.path("name").asText(null);
			if (name != null && !name.isEmpty()) {
				NAME_CACHE.put(uuidString, name);
				return name;
			}
		} catch (Exception ignored) {
		}

		return uuidString;
	}

}
