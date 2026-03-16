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
	 * Resolves a Minecraft player name from a UUID string, with an optional fallback name
	 * for offline-mode UUIDs.
	 * <p>
	 * If the UUID is an offline-mode UUID (version 3), {@code offlineFallbackName} is returned
	 * if non-null; otherwise "N/A" is returned. For online-mode UUIDs (version 4), the Mojang
	 * session server is queried. Network failures fall back to the raw UUID.
	 *
	 * @param uuidString          The UUID string (standard dashed format).
	 * @param offlineFallbackName The player name to use for offline UUIDs, or null.
	 * @return The resolved player name, or the fallback/"N/A"/UUID string if resolution fails.
	 */
	public static String resolvePlayerName(String uuidString, String offlineFallbackName) {
		String cached = NAME_CACHE.get(uuidString);
		if (cached != null) {
			return cached;
		}

		try {
			UUID uuid = UUID.fromString(uuidString);

			// Check if this is an offline-mode UUID (version 3)
			if (uuid.version() == 3) {
				// Offline UUIDs are generated from "OfflinePlayer:" + name
				// We cannot reverse this, so use the fallback name or "N/A"
				String name = (offlineFallbackName != null) ? offlineFallbackName : "N/A";
				NAME_CACHE.put(uuidString, name);
				return name;
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
