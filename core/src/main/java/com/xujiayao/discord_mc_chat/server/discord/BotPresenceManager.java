package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles Discord bot presence updates.
 *
 * @author Xujiayao
 */
public final class BotPresenceManager {

	// Bursts of join/quit events are collapsed into a single presence update: the first call schedules the
	// update this far in the future, and every call arriving before then only pushes the existing schedule back.
	private static final long UPDATE_DEBOUNCE_MILLIS = 500;
	private static final long UPDATE_INTERVAL_SECONDS = 30;

	private static ScheduledExecutorService statusUpdateExecutor;
	private static ScheduledFuture<?> presenceUpdateTask;

	// True while the scheduled task owns the bot presence, i.e. since the last update() that had at least
	// one presence switch enabled. Guarded by BotPresenceManager.class.
	private static boolean presenceManaged;

	private BotPresenceManager() {
	}

	/**
	 * Updates the Discord bot's status and activity based on the current server state.
	 * <p>
	 * Rapid calls are debounced ({@value #UPDATE_DEBOUNCE_MILLIS} ms) and the resulting task refreshes the
	 * presence every {@value #UPDATE_INTERVAL_SECONDS} seconds. The previous task is always cancelled first,
	 * including when both presence switches are disabled: otherwise a task scheduled with the old
	 * configuration would keep running forever.
	 */
	public static void update() {
		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			// JDA is gone: make sure no stale task keeps running against a dead instance.
			synchronized (BotPresenceManager.class) {
				presenceUpdateTask = cancelPresenceTask();
				presenceManaged = false;
			}
			return;
		}

		boolean enableStatus = ConfigManager.getBoolean("discord.bot.enable_status");
		boolean enableActivity = ConfigManager.getBoolean("discord.bot.enable_activity");

		synchronized (BotPresenceManager.class) {
			presenceUpdateTask = cancelPresenceTask();

			if (!enableStatus && !enableActivity) {
				// Nothing is managed any more: hand the presence back to its default state once, so the bot
				// does not stay at the last status/activity that DMCC applied.
				if (presenceManaged) {
					presenceManaged = false;
					resetPresence(jda);
				}
				return;
			}

			if (statusUpdateExecutor == null || statusUpdateExecutor.isShutdown()) {
				statusUpdateExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-BotPresence"));
			}

			presenceManaged = true;
			presenceUpdateTask = statusUpdateExecutor.scheduleWithFixedDelay(() -> {
				try {
					doUpdateBotPresence(enableStatus, enableActivity);
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.presence_update_failed", e.getMessage()));
				}
			}, UPDATE_DEBOUNCE_MILLIS, UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
		}
	}

	/**
	 * Cancels the scheduled presence update, if any.
	 *
	 * @return Always {@code null}, so callers can assign the result back to {@link #presenceUpdateTask}.
	 */
	private static ScheduledFuture<?> cancelPresenceTask() {
		if (presenceUpdateTask != null) {
			presenceUpdateTask.cancel(false);
		}
		return null;
	}

	/**
	 * Restores the presence the bot would have without DMCC managing it: online, without an activity.
	 */
	private static void resetPresence(JDA jda) {
		try {
			jda.getPresence().setStatus(OnlineStatus.ONLINE);
			jda.getPresence().setActivity(null);
		} catch (RejectedExecutionException ignored) {
			// JDA may reject tasks during shutdown races; nothing is left to reset at that point.
		}
	}

	private static void doUpdateBotPresence(boolean enableStatus, boolean enableActivity) {
		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			return;
		}

		int onlinePlayerCount = 0;
		int maxPlayerCount = 0;
		int onlineServerCount = 0;

		List<String> connectedClients = NetworkManager.getConnectedClientNames();
		if (!connectedClients.isEmpty()) {
			Map<String, Packets.InfoSnapshot> infoMap = NetworkManager.requestInfoSnapshot(3);
			for (String client : connectedClients) {
				Packets.InfoSnapshot info = infoMap.get(client);
				if (info != null && info.maxPlayerCount() > 0) {
					onlinePlayerCount += info.onlinePlayerCount();
					maxPlayerCount += info.maxPlayerCount();
					onlineServerCount++;
				}
			}
		}

		if (enableStatus) {
			if (onlineServerCount == 0) {
				jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
			} else if (onlinePlayerCount == 0) {
				jda.getPresence().setStatus(OnlineStatus.IDLE);
			} else {
				jda.getPresence().setStatus(OnlineStatus.ONLINE);
			}
		}

		if (enableActivity) {
			JsonNode customMessages = I18nManager.getCustomMessages();
			if (customMessages != null) {
				String activityText;
				if (onlineServerCount == 0) {
					activityText = customMessages.path("activity").path("all_servers_offline").asString();
				} else {
					activityText = customMessages.path("activity").path("at_least_one_server_online").asString();
				}

				activityText = activityText.replace("{online_player_count}", String.valueOf(onlinePlayerCount))
						.replace("{max_player_count}", String.valueOf(maxPlayerCount));
				jda.getPresence().setActivity(Activity.playing(activityText));
			}
		}
	}

	static void shutdown() {
		synchronized (BotPresenceManager.class) {
			presenceUpdateTask = cancelPresenceTask();
			presenceManaged = false;
			if (statusUpdateExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(statusUpdateExecutor);
				statusUpdateExecutor = null;
			}
		}
	}
}
