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
import net.dv8tion.jda.api.entities.SelfMember;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Keeps Discord guild nicknames in sync with the account linking state.
 * <p>
 * Linked members always wear the rendered {@code account_linking.nickname_sync.template}, and unlinked
 * members always wear no nickname at all (their plain Discord username), which is what stops anyone
 * from impersonating another user by renaming themselves. A member whose nickname already matches is
 * never written to, so the steady state costs no REST calls.
 * <p>
 * Every entry point funnels into {@link #syncForDiscordUser(String)}: linking, unlinking, joining
 * Minecraft and self-renaming. {@link #sweepAll()} additionally covers nicknames that were changed
 * while DMCC was offline, and is meant to run once per JDA session, never while the linked accounts
 * are still being loaded.
 *
 * @author Xujiayao
 */
public final class NicknameSyncManager {

	// Coalesces a member renaming themselves over and over without delaying deliberate syncs
	private static final long TAMPER_COOLDOWN_MILLIS = 3_000L;

	private static final int MAX_COOLDOWN_ENTRIES = 4096;

	private static final String PLAYER_NAME_PLACEHOLDER = "{player_name}";
	private static final String EFFECTIVE_NAME_PLACEHOLDER = "{effective_name}";
	private static final String DISCORD_NAME_PLACEHOLDER = "{discord_name}";

	private static final Map<String, Long> TAMPER_HANDLED_AT = new ConcurrentHashMap<>();

	// One warning per guild and reason, so a permission problem cannot flood the log every sweep
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private static ExecutorService syncExecutor;

	private NicknameSyncManager() {
	}

	/**
	 * Re-applies the expected nickname to every member of every guild DMCC is in, including the
	 * nicknames that were changed while DMCC was offline.
	 */
	public static void sweepAll() {
		submit(() -> {
			JDA jda = availableJda();
			if (jda == null) {
				return;
			}

			String template = template();
			if (template == null) {
				return;
			}

			int checked = 0;
			int updated = 0;
			for (Guild guild : jda.getGuilds()) {
				if (!canManageNicknames(guild)) {
					continue;
				}
				for (Member member : guild.getMembers()) {
					if (isUntouchable(member)) {
						continue;
					}
					checked++;
					if (syncMember(guild, member, template)) {
						updated++;
					}
				}
			}

			LOGGER.info(I18nManager.getDmccTranslation("discord.nickname_sync.sweep_done", checked, updated));
		});
	}

	/**
	 * Re-applies the expected nickname of one Discord user in every guild DMCC shares with them.
	 * <p>
	 * For an unlinked user the expected nickname is null, which removes a nickname that was set by hand.
	 *
	 * @param discordId The Discord user ID, ignored when null.
	 */
	public static void syncForDiscordUser(String discordId) {
		if (discordId == null) {
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

			for (Guild guild : jda.getGuilds()) {
				Member member = guild.getMemberById(discordId);
				if (member == null || isUntouchable(member) || !canManageNicknames(guild)) {
					continue;
				}
				syncMember(guild, member, template);
			}
		});
	}

	/**
	 * Handles a member renaming themselves: linked members are put back on the template value, and
	 * unlinked members lose the nickname again.
	 *
	 * @param discordId The Discord user ID, ignored when null.
	 */
	public static void onNicknameChanged(String discordId) {
		if (discordId == null) {
			return;
		}

		long now = System.currentTimeMillis();
		if (TAMPER_HANDLED_AT.size() > MAX_COOLDOWN_ENTRIES) {
			// Same trade-off as MojangUtils: the map is a cooldown, not a record worth keeping
			TAMPER_HANDLED_AT.clear();
		}
		Long previous = TAMPER_HANDLED_AT.put(discordId, now);
		if (previous != null && now - previous < TAMPER_COOLDOWN_MILLIS) {
			// A member renaming themselves in a loop is answered at most once per cooldown; JDA's
			// own rate limiter bounds the REST side on top of this
			return;
		}

		syncForDiscordUser(discordId);
	}

	public static void shutdown() {
		synchronized (NicknameSyncManager.class) {
			if (syncExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(syncExecutor);
				syncExecutor = null;
			}
		}

		TAMPER_HANDLED_AT.clear();
		WARNED.clear();
	}

	private static void submit(Runnable task) {
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

	private static ExecutorService getOrCreateExecutor() {
		synchronized (NicknameSyncManager.class) {
			if (syncExecutor == null || syncExecutor.isShutdown()) {
				syncExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-NicknameSync"));
			}
			return syncExecutor;
		}
	}

	/**
	 * @return true when a nickname update was actually issued for this member.
	 */
	private static boolean syncMember(Guild guild, Member member, String template) {
		SelfMember self = guild.getSelfMember();
		if (!self.canInteract(member)) {
			warnOnce("cannot_interact:" + guild.getId(), "discord.nickname_sync.cannot_interact", guild.getName());
			return false;
		}

		List<LinkedAccountManager.LinkEntry> entries = LinkedAccountManager.getLinkEntriesByDiscordId(member.getId());
		if (entries.isEmpty()) {
			// Unlinked members must keep their plain Discord username
			return applyNickname(member, null);
		}

		String playerName = latestPlayerName(entries);
		String expected = template
				.replace(PLAYER_NAME_PLACEHOLDER, playerName)
				.replace(EFFECTIVE_NAME_PLACEHOLDER, playerName)
				.replace(DISCORD_NAME_PLACEHOLDER, member.getUser().getName());
		if (expected.isBlank()) {
			// A template that renders to nothing is a configuration error, never "clear the nickname"
			warnOnce("template", "discord.nickname_sync.template_invalid");
			return false;
		}

		return applyNickname(member, expected.length() > Member.MAX_NICKNAME_LENGTH
				? expected.substring(0, Member.MAX_NICKNAME_LENGTH)
				: expected);
	}

	private static boolean applyNickname(Member member, String expected) {
		if (Objects.equals(member.getNickname(), expected)) {
			// Steady state: nothing to do and no REST call to make
			return false;
		}

		member.modifyNickname(expected).queue(
				_ -> {
				},
				error -> LOGGER.warn(I18nManager.getDmccTranslation("discord.nickname_sync.update_failed",
						member.getId(), String.valueOf(error.getMessage()))));
		return true;
	}

	/**
	 * Player names are shared by every member linked to the same account, and the newest link decides
	 * the name when a Discord user is allowed to link more than one account.
	 */
	private static String latestPlayerName(List<LinkedAccountManager.LinkEntry> entries) {
		LinkedAccountManager.LinkEntry newest = entries.getFirst();
		for (LinkedAccountManager.LinkEntry entry : entries) {
			if (entry.linkedAt() > newest.linkedAt()) {
				newest = entry;
			}
		}
		return MojangUtils.resolvePlayerName(newest.minecraftUuid(), newest.offlinePlayerName());
	}

	/**
	 * @return The configured template, or null when nickname syncing is disabled or misconfigured.
	 */
	private static String template() {
		if (!ConfigManager.getBoolean("account_linking.nickname_sync.enable", false)) {
			return null;
		}

		String template = ConfigManager.getString("account_linking.nickname_sync.template", "");
		if (template == null || template.isBlank()) {
			warnOnce("template", "discord.nickname_sync.template_invalid");
			return null;
		}

		return template;
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
		if (WARNED.add(key)) {
			LOGGER.warn(I18nManager.getDmccTranslation(messageKey, args));
		}
	}
}
