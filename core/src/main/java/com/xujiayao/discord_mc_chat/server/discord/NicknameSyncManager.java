package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Keeps Discord guild nicknames in sync with the account linking state.
 * <p>
 * Linked members always wear the rendered {@code account_linking.nickname_sync.template}, while unlinked
 * members always wear no nickname at all (their plain Discord username), which is what stops anyone from
 * impersonating another user by renaming themselves. A member whose nickname already matches is never
 * written to, so the steady state costs no REST calls.
 * <p>
 * All work runs on a dedicated single-thread executor: the Minecraft server thread is never blocked by
 * member loads, REST lookups or nickname writes, and the coordinator never blocks on JDA's event thread.
 * Every entry point funnels into {@link #syncForDiscordUser(String)} (linking, unlinking, guild join,
 * joining Minecraft), {@link #onMemberUpdated(String)} (a member changed something, debounced) or
 * {@link #sweepAll()} (startup/reload full scan). {@link #start()} additionally schedules a periodic
 * reconciliation of all linked users so a missed event or a transient REST failure can never leave a
 * nickname permanently out of sync.
 *
 * @author Xujiayao
 */
public final class NicknameSyncManager {

	// Coalesces repeated member updates into one check; a write of our own therefore only causes a
	// compare-and-do-nothing run, never a write loop.
	private static final long DEBOUNCE_MILLIS = 1_500L;

	// Linked accounts are few; a periodic comparison heals missed gateway events and failed writes.
	private static final long RECONCILE_INTERVAL_MINUTES = 5L;

	private static final long MEMBER_REST_TIMEOUT_SECONDS = 10L;
	private static final long MISSING_MEMBER_TTL_MILLIS = 60_000L;
	private static final Duration MEMBER_LOAD_TIMEOUT = Duration.ofSeconds(60);

	private static final int MAX_TRACKED_ENTRIES = 4096;

	// A guild is considered to need an explicit member load only when it is clearly incomplete; the
	// member count is approximate on Discord's side, so a small difference is normal after chunking.
	private static final int INCOMPLETE_MEMBER_FRACTION = 2;

	private static final String PLAYER_NAME_PLACEHOLDER = "{player_name}";
	private static final String EFFECTIVE_NAME_PLACEHOLDER = "{effective_name}";
	private static final String DISCORD_NAME_PLACEHOLDER = "{discord_name}";
	private static final String UNRESOLVED_NAME = "N/A";

	// Freshest Minecraft name per UUID, filled from link/join packets so a rename is picked up without
	// waiting for a process restart (MojangUtils caches names for the lifetime of the process).
	private static final Map<String, String> KNOWN_PLAYER_NAMES = new ConcurrentHashMap<>();

	// Negative cache for guild/user pairs the REST lookup proved absent, to avoid a REST call per guild.
	private static final Map<String, Long> MISSING_MEMBER_UNTIL = new ConcurrentHashMap<>();

	private static final Map<String, ScheduledFuture<?>> PENDING_USER_SYNC = new ConcurrentHashMap<>();

	// One warning per reason/target per session, so permission or hierarchy problems cannot flood the log.
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private static ScheduledExecutorService executor;
	private static ScheduledFuture<?> reconcileTask;
	private static volatile boolean running;

	private NicknameSyncManager() {
	}

	/**
	 * Starts the periodic reconciliation. Does nothing when the feature is disabled; idempotent.
	 */
	public static void start() {
		if (!isEnabled()) {
			return;
		}

		synchronized (NicknameSyncManager.class) {
			if (executor == null || executor.isShutdown()) {
				executor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-NicknameSync"));
			}
			running = true;
			if (reconcileTask != null) {
				reconcileTask.cancel(false);
			}
			reconcileTask = executor.scheduleWithFixedDelay(() -> {
				try {
					reconcile();
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.failed", e.toString()));
				}
			}, RECONCILE_INTERVAL_MINUTES, RECONCILE_INTERVAL_MINUTES, TimeUnit.MINUTES);
		}
	}

	/**
	 * Re-applies the expected nickname to every member of every guild DMCC is in, including the
	 * nicknames that were changed while DMCC was offline. Runs asynchronously; the caller never blocks.
	 */
	public static void sweepAll() {
		if (!isEnabled()) {
			return;
		}
		submit(NicknameSyncManager::beginSweep);
	}

	/**
	 * Re-applies the expected nickname of one Discord user in every guild DMCC shares with them.
	 * <p>
	 * For an unlinked user the expected nickname is null, which removes a nickname that was set by hand.
	 *
	 * @param discordId The Discord user ID, ignored when null.
	 */
	public static void syncForDiscordUser(String discordId) {
		if (discordId == null || !isEnabled()) {
			return;
		}

		submit(() -> {
			JDA jda = availableJda();
			if (jda == null) {
				return;
			}
			String template = template();
			if (template == null) {
				return;
			}
			syncUser(discordId, jda, template);
		});
	}

	/**
	 * Handles any Discord member update. The expected nickname is re-applied (linked members) or the
	 * nickname is cleared (unlinked members); updates are coalesced per user to avoid a storm when a
	 * member edits themselves repeatedly. Uses the generic member update event, which JDA also fires for
	 * members that were not cached yet.
	 *
	 * @param discordId The Discord user ID, ignored when null.
	 */
	public static void onMemberUpdated(String discordId) {
		if (discordId == null || !isEnabled() || !running) {
			return;
		}

		synchronized (PENDING_USER_SYNC) {
			ScheduledFuture<?> previous = PENDING_USER_SYNC.remove(discordId);
			if (previous != null) {
				previous.cancel(false);
			}

			try {
				PENDING_USER_SYNC.put(discordId, getOrCreateExecutor().schedule(() -> {
					PENDING_USER_SYNC.remove(discordId);
					try {
						JDA jda = availableJda();
						if (jda == null) {
							return;
						}
						String template = template();
						if (template == null) {
							return;
						}
						syncUser(discordId, jda, template);
					} catch (Exception e) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.failed", e.toString()));
					}
				}, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS));
			} catch (RejectedExecutionException e) {
				LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.failed", e.toString()));
			}
		}
	}

	/**
	 * Handles a guild member joining: clears the negative cache entry for that guild/user pair (the member
	 * demonstrably exists now) and re-applies the expected nickname immediately.
	 */
	public static void onMemberJoined(String guildId, String discordId) {
		if (guildId != null && discordId != null) {
			MISSING_MEMBER_UNTIL.remove(guildId + ":" + discordId);
		}
		syncForDiscordUser(discordId);
	}

	/**
	 * Remembers the freshest Minecraft player name for a UUID. Called from link/join handling so the
	 * nickname template follows renames instead of the process-lifetime Mojang name cache.
	 */
	public static void rememberPlayerName(String uuid, String name) {
		if (uuid == null || uuid.isBlank() || name == null || name.isBlank()) {
			return;
		}
		if (KNOWN_PLAYER_NAMES.size() > MAX_TRACKED_ENTRIES) {
			KNOWN_PLAYER_NAMES.clear();
		}
		KNOWN_PLAYER_NAMES.put(uuid, name);
	}

	public static void shutdown() {
		running = false;
		synchronized (NicknameSyncManager.class) {
			if (reconcileTask != null) {
				reconcileTask.cancel(false);
				reconcileTask = null;
			}
			if (executor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(executor);
				executor = null;
			}
		}

		PENDING_USER_SYNC.values().forEach(future -> future.cancel(false));
		PENDING_USER_SYNC.clear();
		KNOWN_PLAYER_NAMES.clear();
		MISSING_MEMBER_UNTIL.clear();
		WARNED.clear();
	}

	private static void beginSweep() {
		JDA jda = availableJda();
		if (jda == null) {
			return;
		}
		String template = template();
		if (template == null) {
			return;
		}

		List<Guild> incomplete = new ArrayList<>();
		for (Guild guild : jda.getGuilds()) {
			if (needsMemberLoad(guild)) {
				incomplete.add(guild);
			}
		}

		if (incomplete.isEmpty()) {
			runSweep(jda, template);
			return;
		}

		AtomicInteger remaining = new AtomicInteger(incomplete.size());
		for (Guild guild : incomplete) {
			try {
				guild.loadMembers()
						.setTimeout(MEMBER_LOAD_TIMEOUT)
						.onSuccess(_ -> onMemberLoadFinished(remaining))
						.onError(error -> {
							warnOnce("member_load:" + guild.getId(), "discord.nickname_sync.member_load_failed",
									guild.getName(), String.valueOf(error.getMessage()));
							onMemberLoadFinished(remaining);
						});
			} catch (Exception e) {
				warnOnce("member_load:" + guild.getId(), "discord.nickname_sync.member_load_failed",
						guild.getName(), e.toString());
				onMemberLoadFinished(remaining);
			}
		}
	}

	private static void onMemberLoadFinished(AtomicInteger remaining) {
		if (remaining.decrementAndGet() == 0) {
			submit(NicknameSyncManager::sweepWithFreshState);
		}
	}

	private static void sweepWithFreshState() {
		JDA jda = availableJda();
		if (jda == null) {
			return;
		}
		String template = template();
		if (template == null) {
			return;
		}
		runSweep(jda, template);
	}

	private static void runSweep(JDA jda, String template) {
		int guildCount = 0;
		int checked = 0;
		int updated = 0;
		int skipped = 0;

		for (Guild guild : jda.getGuilds()) {
			guildCount++;
			if (!canManageNicknames(guild)) {
				continue;
			}

			int cached = guild.getMembers().size();
			int expectedCount = guild.getMemberCount();
			if (expectedCount > 0 && cached < expectedCount / INCOMPLETE_MEMBER_FRACTION) {
				warnOnce("members_incomplete:" + guild.getId(), "discord.nickname_sync.members_incomplete",
						guild.getName(), cached, expectedCount);
			}

			for (Member member : guild.getMembers()) {
				if (isUntouchable(member)) {
					continue;
				}
				checked++;
				switch (syncMember(guild, member, template, false)) {
					case UPDATED -> updated++;
					case SKIPPED -> skipped++;
					case UNCHANGED -> {
					}
				}
			}
		}

		LOGGER.info(I18nManager.getDmccTranslation("discord.nickname_sync.sweep_done", guildCount, checked, updated, skipped));
	}

	private static void reconcile() {
		if (!isEnabled()) {
			return;
		}
		JDA jda = availableJda();
		if (jda == null) {
			return;
		}
		String template = template();
		if (template == null) {
			return;
		}

		for (String discordId : new ArrayList<>(LinkedAccountManager.getAllLinks().keySet())) {
			syncUser(discordId, jda, template);
		}
	}

	private static void syncUser(String discordId, JDA jda, String template) {
		for (Guild guild : jda.getGuilds()) {
			if (!canManageNicknames(guild)) {
				continue;
			}

			Member member = resolveMember(guild, discordId);
			if (member == null || isUntouchable(member)) {
				continue;
			}

			syncMember(guild, member, template, true);
		}
	}

	private static SyncResult syncMember(Guild guild, Member member, String template, boolean announce) {
		if (!guild.getSelfMember().canInteract(member)) {
			warnOnce("cannot_manage_member:" + guild.getId() + ":" + member.getId(),
					"discord.nickname_sync.cannot_manage_member", guild.getName(), member.getEffectiveName());
			return SyncResult.SKIPPED;
		}

		List<LinkedAccountManager.LinkEntry> entries = LinkedAccountManager.getLinkEntriesByDiscordId(member.getId());
		String expected;
		if (entries.isEmpty()) {
			// Unlinked members must keep their plain Discord username
			expected = null;
		} else {
			String playerName = resolvePlayerName(entries);
			if (playerName == null) {
				warnOnce("name_unresolved:" + member.getId(), "discord.nickname_sync.name_unresolved",
						entries.getFirst().minecraftUuid());
				return SyncResult.SKIPPED;
			}

			expected = renderNickname(template, playerName, member.getUser().getName());
			if (expected == null) {
				warnOnce("template", "discord.nickname_sync.template_invalid");
				return SyncResult.SKIPPED;
			}
		}

		return applyNickname(guild, member, expected, announce) ? SyncResult.UPDATED : SyncResult.UNCHANGED;
	}

	private static boolean applyNickname(Guild guild, Member member, String expected, boolean announce) {
		if (Objects.equals(member.getNickname(), expected)) {
			// Steady state: nothing to do and no REST call to make
			return false;
		}

		try {
			member.modifyNickname(expected).queue(
					_ -> {
						if (announce) {
							LOGGER.info(I18nManager.getDmccTranslation("discord.nickname_sync.updated",
									member.getEffectiveName(), guild.getName(), expected == null ? "" : expected));
						}
					},
					error -> warnOnce("update_failed:" + guild.getId() + ":" + member.getId() + ":" + error.getClass().getSimpleName(),
							"discord.nickname_sync.update_failed", member.getEffectiveName(), guild.getName(),
							String.valueOf(error.getMessage())));
		} catch (Exception e) {
			// Permission/hierarchy can change between the checks above and this call; log once and let the
			// next trigger or the periodic reconciliation retry instead of aborting the whole scan.
			warnOnce("update_failed:" + guild.getId() + ":" + member.getId() + ":" + e.getClass().getSimpleName(),
					"discord.nickname_sync.update_failed", member.getEffectiveName(), guild.getName(), e.toString());
			return false;
		}
		return true;
	}

	private static Member resolveMember(Guild guild, String discordId) {
		Member member = guild.getMemberById(discordId);
		if (member != null) {
			return member;
		}

		String cacheKey = guild.getId() + ":" + discordId;
		Long missingUntil = MISSING_MEMBER_UNTIL.get(cacheKey);
		if (missingUntil != null && missingUntil > System.currentTimeMillis()) {
			return null;
		}

		try {
			Member retrieved = guild.retrieveMemberById(discordId)
					.timeout(MEMBER_REST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
					.complete();
			if (retrieved != null) {
				return retrieved;
			}
		} catch (ErrorResponseException e) {
			if (e.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER) {
				warnOnce("member_retrieve:" + cacheKey + ":" + e.getErrorResponse(),
						"discord.nickname_sync.member_retrieve_failed", guild.getName(),
						String.valueOf(e.getErrorResponse()));
			}
		} catch (Exception e) {
			// Timeout or transient HTTP failure: the next trigger/reconciliation retries; no REST storm.
		}

		cacheMissingMember(cacheKey);
		return null;
	}

	private static void cacheMissingMember(String cacheKey) {
		if (MISSING_MEMBER_UNTIL.size() > MAX_TRACKED_ENTRIES) {
			MISSING_MEMBER_UNTIL.clear();
		}
		MISSING_MEMBER_UNTIL.put(cacheKey, System.currentTimeMillis() + MISSING_MEMBER_TTL_MILLIS);
	}

	/**
	 * Picks the player name of the newest linked Minecraft account that can be resolved; an older link is
	 * used as a fallback when the newest account cannot be resolved, instead of writing a placeholder into
	 * the nickname. Package-private with injected lookups so the selection is unit-testable.
	 */
	static String choosePlayerName(List<LinkedAccountManager.LinkEntry> entries,
	                               Function<String, String> knownNameLookup,
	                               BiFunction<String, String, String> mojangLookup) {
		if (entries == null || entries.isEmpty()) {
			return null;
		}

		List<LinkedAccountManager.LinkEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingLong(LinkedAccountManager.LinkEntry::linkedAt).reversed());
		for (LinkedAccountManager.LinkEntry entry : sorted) {
			String name = resolveEntryPlayerName(entry, knownNameLookup, mojangLookup);
			if (name != null) {
				return name;
			}
		}
		return null;
	}

	private static String resolvePlayerName(List<LinkedAccountManager.LinkEntry> entries) {
		return choosePlayerName(entries, KNOWN_PLAYER_NAMES::get, MojangUtils::resolvePlayerName);
	}

	private static String resolveEntryPlayerName(LinkedAccountManager.LinkEntry entry,
	                                             Function<String, String> knownNameLookup,
	                                             BiFunction<String, String, String> mojangLookup) {
		if (entry == null || entry.minecraftUuid() == null || entry.minecraftUuid().isBlank()) {
			return null;
		}

		String knownName = knownNameLookup.apply(entry.minecraftUuid());
		if (knownName != null && !knownName.isBlank()) {
			return knownName;
		}

		if (entry.offlinePlayerName() != null && !entry.offlinePlayerName().isBlank()) {
			return entry.offlinePlayerName();
		}

		try {
			String resolved = mojangLookup.apply(entry.minecraftUuid(), entry.offlinePlayerName());
			if (resolved != null && !resolved.isBlank()
					&& !resolved.equals(entry.minecraftUuid())
					&& !UNRESOLVED_NAME.equals(resolved)) {
				return resolved;
			}
		} catch (Exception ignored) {
			// Treated as an unresolved name: skip this member instead of writing a placeholder nickname.
		}
		return null;
	}

	/**
	 * Renders the configured template, sanitizes it and truncates it to Discord's nickname limit.
	 *
	 * @return The nickname to apply, or null when the template renders to nothing (a configuration error
	 * that must never clear a nickname).
	 */
	static String renderNickname(String template, String playerName, String discordName) {
		if (template == null) {
			return null;
		}

		String rendered = template
				.replace(PLAYER_NAME_PLACEHOLDER, playerName == null ? "" : playerName)
				.replace(EFFECTIVE_NAME_PLACEHOLDER, playerName == null ? "" : playerName)
				.replace(DISCORD_NAME_PLACEHOLDER, discordName == null ? "" : discordName);
		rendered = sanitizeNickname(rendered);
		if (rendered.isEmpty()) {
			return null;
		}

		return truncateNickname(rendered);
	}

	/**
	 * Replaces control characters (including line breaks) with spaces and trims the result.
	 */
	static String sanitizeNickname(String value) {
		if (value == null) {
			return "";
		}

		StringBuilder sanitized = new StringBuilder(value.length());
		value.codePoints().forEach(codePoint -> sanitized.appendCodePoint(Character.isISOControl(codePoint) ? ' ' : codePoint));

		int start = 0;
		int end = sanitized.length();
		while (start < end && Character.isWhitespace(sanitized.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(sanitized.charAt(end - 1))) {
			end--;
		}
		return sanitized.substring(start, end);
	}

	/**
	 * Truncates by Unicode code points so an emoji cannot be cut in half.
	 */
	static String truncateNickname(String value) {
		if (value == null) {
			return null;
		}
		if (value.codePointCount(0, value.length()) <= Member.MAX_NICKNAME_LENGTH) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, Member.MAX_NICKNAME_LENGTH));
	}

	private static boolean needsMemberLoad(Guild guild) {
		int expectedCount = guild.getMemberCount();
		if (expectedCount <= 0) {
			return false;
		}
		int cached = guild.getMembers().size();
		return cached == 0 || cached < expectedCount / INCOMPLETE_MEMBER_FRACTION;
	}

	/**
	 * @return The configured template, or null when nickname syncing is disabled or misconfigured.
	 */
	private static String template() {
		if (!isEnabled()) {
			return null;
		}

		String template = ConfigManager.getString("account_linking.nickname_sync.template", "");
		if (template == null || template.isBlank()) {
			warnOnce("template", "discord.nickname_sync.template_invalid");
			return null;
		}
		return template;
	}

	private static boolean isEnabled() {
		return ConfigManager.getBoolean("account_linking.nickname_sync.enable", false);
	}

	private static JDA availableJda() {
		JDA jda = DiscordManager.getJda();
		return (jda != null && jda.getStatus() == JDA.Status.CONNECTED) ? jda : null;
	}

	/**
	 * Bots are never renamed: DMCC's own user is one, and other bots are not DMCC's business.
	 */
	private static boolean isUntouchable(Member member) {
		return member.getUser().isBot();
	}

	private static boolean canManageNicknames(Guild guild) {
		if (!guild.getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)) {
			warnOnce("missing_permission:" + guild.getId(), "discord.nickname_sync.missing_permission", guild.getName());
			return false;
		}
		return true;
	}

	private static void warnOnce(String key, String messageKey, Object... args) {
		if (WARNED.size() > MAX_TRACKED_ENTRIES) {
			WARNED.clear();
		}
		if (WARNED.add(key)) {
			LOGGER.warn(I18nManager.getDmccTranslation(messageKey, args));
		}
	}

	private static void submit(Runnable task) {
		if (!running) {
			return;
		}

		try {
			getOrCreateExecutor().execute(() -> {
				try {
					task.run();
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.failed", e.toString()));
				}
			});
		} catch (RejectedExecutionException e) {
			LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.failed", e.toString()));
		}
	}

	private static ScheduledExecutorService getOrCreateExecutor() {
		synchronized (NicknameSyncManager.class) {
			if (!running) {
				// Never resurrect the executor after shutdown (for example while JDA is tearing down).
				throw new RejectedExecutionException("Nickname sync is not running");
			}
			if (executor == null || executor.isShutdown()) {
				executor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-NicknameSync"));
			}
			return executor;
		}
	}

	private enum SyncResult {
		UNCHANGED,
		UPDATED,
		SKIPPED
	}
}
