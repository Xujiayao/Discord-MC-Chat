package com.xujiayao.discord_mc_chat.utils;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;

/**
 * Utilities for resolving Minecraft player names from UUIDs.
 * <p>
 * Supports both online (Mojang API) and offline UUID formats.
 * Provides fallback to raw UUID display when resolution fails.
 * Results - including failures - are cached in memory to avoid repeated network calls.
 *
 * @author Xujiayao
 */
public final class MojangUtils {

	private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

	/**
	 * How long a successful Mojang lookup is trusted. Player names change rarely.
	 */
	private static final long SUCCESS_TTL_MILLIS = Duration.ofHours(24).toMillis();

	/**
	 * How long a failed lookup is remembered.
	 * <p>
	 * Without negative caching a Mojang outage made every single chat message retry the HTTP call for every
	 * unresolved UUID; this short window keeps the failure cost bounded while still recovering quickly.
	 */
	private static final long FAILURE_TTL_MILLIS = Duration.ofMinutes(5).toMillis();

	private static final Map<String, Entry> NAME_CACHE = new ConcurrentHashMap<>();

	private MojangUtils() {
	}

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
		Entry cached = NAME_CACHE.get(uuidString);
		if (cached != null && !cached.expired()) {
			return cached.name();
		}

		try {
			UUID uuid = UUID.fromString(uuidString);

			// Check if this is an offline-mode UUID (version 3)
			if (uuid.version() == 3) {
				// Offline UUIDs are generated from "OfflinePlayer:" + name
				// We cannot reverse this, so use the fallback name or "N/A".
				// This mapping can never change, so it is cached without expiry.
				String name = (offlineFallbackName != null) ? offlineFallbackName : "N/A";
				NAME_CACHE.put(uuidString, new Entry(name, Long.MAX_VALUE));
				return name;
			}

			// Online UUID (version 4) - query Mojang
			String dashlessUuid = uuidString.replace("-", "");
			String response = HttpUtils.get(PROFILE_URL + dashlessUuid);
			JsonNode profile = JSON_MAPPER.readTree(response);

			String name = profile.path("name").asString(null);
			if (name != null && !name.isEmpty()) {
				NAME_CACHE.put(uuidString, new Entry(name, System.currentTimeMillis() + SUCCESS_TTL_MILLIS));
				return name;
			}
		} catch (Exception ignored) {
			// Fall through to the fallback below; the failure is cached so an outage does not turn into one
			// HTTP request per chat message.
		}

		long retryAt = System.currentTimeMillis() + FAILURE_TTL_MILLIS;
		if (cached != null) {
			// This UUID resolved before, so keep serving the last known name rather than regressing to the
			// raw UUID just because Mojang was briefly unreachable.
			NAME_CACHE.put(uuidString, new Entry(cached.name(), retryAt));
			return cached.name();
		}

		NAME_CACHE.put(uuidString, new Entry(uuidString, retryAt));
		return uuidString;
	}

	/**
	 * A cached resolution result.
	 *
	 * @param name      The value to return.
	 * @param expiresAt Epoch millis after which the entry must be resolved again.
	 */
	private record Entry(String name, long expiresAt) {

		private boolean expired() {
			return System.currentTimeMillis() >= expiresAt;
		}
	}

}
