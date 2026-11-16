package com.xujiayao.discord_mc_chat.utils;

import okhttp3.OkHttpClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.OK_HTTP_CLIENT;

/**
 * Utilities for resolving Minecraft player names from UUIDs. Supports both online (Mojang API) and offline
 * UUID formats, falls back to raw UUID display when resolution fails, and caches results in memory.
 *
 * @author Xujiayao
 */
public final class MojangUtils {

	private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

	/**
	 * Upper bound of a single profile lookup: the client derives from the global client (same connection pool
	 * and dispatcher) but caps one call at 5 seconds, which bounds how long a caller can block.
	 */
	private static final long PROFILE_CALL_TIMEOUT_SECONDS = 5;
	private static final OkHttpClient PROFILE_CLIENT = OK_HTTP_CLIENT.newBuilder()
			.callTimeout(PROFILE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.build();

	/**
	 * Positive cache of UUID string to resolved player name. Capacity: 4096 entries; once the cap is reached
	 * the cache is cleared and refilled on demand, so a long-running process cannot grow it without bound.
	 * Invalidation: none — a resolved name never changes.
	 */
	private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();

	/**
	 * Negative cache of UUID string to the epoch millisecond until which resolution is not retried.
	 * TTL: 60 seconds — short enough to recover quickly from a transient Mojang outage or rate limit, long
	 * enough that a chatty server does not repeat a blocking lookup for every message. Capacity: 4096 entries,
	 * cleared when the cap is reached.
	 */
	private static final Map<String, Long> FAILURE_CACHE = new ConcurrentHashMap<>();
	private static final long FAILURE_CACHE_TTL_MILLIS = 60_000L;
	private static final int CACHE_MAX_ENTRIES = 4096;

	/**
	 * Diagnostic reason of the last failed lookup per UUID, kept so a caller that sees the negative cache can
	 * still explain why the name is unavailable.
	 */
	private static final Map<String, String> FAILURE_REASONS = new ConcurrentHashMap<>();

	private MojangUtils() {
	}

	/**
	 * The outcome of a player name lookup. Exactly one of {@code name} and {@code failure} is non-null.
	 *
	 * @param name    The resolved player name, or null when the lookup failed.
	 * @param failure A human-readable failure reason, or null when the lookup succeeded.
	 */
	public record PlayerNameLookup(String name, String failure) {
	}

	/**
	 * Resolves a Minecraft player name and reports the reason when it cannot be resolved.
	 * <p>
	 * Offline-mode UUIDs (version 3) yield {@code offlineFallbackName} if non-null (otherwise "N/A") without
	 * a network call; all other UUIDs are looked up on the Mojang session server. Failed lookups are cached
	 * negatively for 60 seconds, and the failure reason stays available for diagnostics.
	 *
	 * @param uuidString          The UUID string (standard dashed format).
	 * @param offlineFallbackName The player name to use for offline UUIDs, or null.
	 * @return The lookup outcome; {@code name} is null when resolution failed.
	 */
	public static PlayerNameLookup lookupPlayerName(String uuidString, String offlineFallbackName) {
		if (uuidString == null || uuidString.isBlank()) {
			return new PlayerNameLookup(null, "empty UUID");
		}

		String cached = NAME_CACHE.get(uuidString);
		if (cached != null) {
			return new PlayerNameLookup(cached, null);
		}

		Long failedUntil = FAILURE_CACHE.get(uuidString);
		if (failedUntil != null) {
			if (failedUntil > System.currentTimeMillis()) {
				// A recent lookup failed; do not block the caller again, but explain why it is unavailable.
				String previous = FAILURE_REASONS.get(uuidString);
				return new PlayerNameLookup(null, "recent lookup failed"
						+ (previous == null ? "" : " (" + previous + ")")
						+ "; retry after the 60s negative cache expires");
			}
			FAILURE_CACHE.remove(uuidString);
		}

		try {
			UUID uuid = UUID.fromString(uuidString);

			if (uuid.version() == 3) {
				// Offline UUIDs are generated from "OfflinePlayer:" + name
				// We cannot reverse this, so use the fallback name or "N/A"
				String name = (offlineFallbackName != null) ? offlineFallbackName : "N/A";
				cacheName(uuidString, name);
				return new PlayerNameLookup(name, null);
			}

			// Online UUID (version 4) - query Mojang
			String dashlessUuid = uuidString.replace("-", "");
			String response = HttpUtils.get(PROFILE_CLIENT, PROFILE_URL + dashlessUuid);
			JsonNode profile = JSON_MAPPER.readTree(response);

			String name = profile.path("name").asString(null);
			if (name != null && !name.isEmpty()) {
				cacheName(uuidString, name);
				return new PlayerNameLookup(name, null);
			}

			String reason = "Mojang response did not contain a player name";
			cacheFailure(uuidString, reason);
			return new PlayerNameLookup(null, reason);
		} catch (Exception e) {
			String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			cacheFailure(uuidString, reason);
			return new PlayerNameLookup(null, reason);
		}
	}

	/**
	 * Resolves a Minecraft player name from a UUID string, with an optional fallback name for offline-mode
	 * UUIDs. Offline-mode UUIDs (version 3) yield {@code offlineFallbackName} if non-null, otherwise "N/A";
	 * online-mode UUIDs (version 4) query the Mojang session server. Network failures fall back to the raw
	 * UUID, and a failed lookup is not retried for the next 60 seconds.
	 *
	 * @param uuidString          The UUID string (standard dashed format).
	 * @param offlineFallbackName The player name to use for offline UUIDs, or null.
	 * @return The resolved player name, or the fallback/"N/A"/UUID string if resolution fails.
	 */
	public static String resolvePlayerName(String uuidString, String offlineFallbackName) {
		PlayerNameLookup lookup = lookupPlayerName(uuidString, offlineFallbackName);
		if (lookup.name() != null) {
			return lookup.name();
		}
		return uuidString;
	}

	private static void cacheName(String uuidString, String name) {
		if (NAME_CACHE.size() >= CACHE_MAX_ENTRIES) {
			NAME_CACHE.clear();
		}
		FAILURE_REASONS.remove(uuidString);
		NAME_CACHE.put(uuidString, name);
	}

	private static void cacheFailure(String uuidString, String reason) {
		if (FAILURE_CACHE.size() >= CACHE_MAX_ENTRIES) {
			FAILURE_CACHE.clear();
			FAILURE_REASONS.clear();
		}
		if (FAILURE_REASONS.size() >= CACHE_MAX_ENTRIES) {
			FAILURE_REASONS.clear();
		}
		FAILURE_CACHE.put(uuidString, System.currentTimeMillis() + FAILURE_CACHE_TTL_MILLIS);
		FAILURE_REASONS.put(uuidString, reason);
	}
}
