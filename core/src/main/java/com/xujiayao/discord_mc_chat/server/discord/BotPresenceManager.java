package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Info.ResponsePacket;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
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

	private static ScheduledExecutorService statusUpdateExecutor;
	private static ScheduledFuture<?> presenceUpdateTask;

	private BotPresenceManager() {
	}

	/**
	 * Updates the Discord bot's status and activity based on the current server state.
	 * Debounce rapid calls and automatically updates every 30 seconds.
	 */
	public static void update() {
		JDA jda = DiscordManager.getJda();
		if (jda == null) return;

		boolean enableStatus = ConfigManager.getBoolean("discord.bot.enable_status");
		boolean enableActivity = ConfigManager.getBoolean("discord.bot.enable_activity");
		if (!enableStatus && !enableActivity) return;

		if (statusUpdateExecutor == null || statusUpdateExecutor.isShutdown()) {
			statusUpdateExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-BotPresence"));
		}

		synchronized (BotPresenceManager.class) {
			if (presenceUpdateTask != null) {
				presenceUpdateTask.cancel(false);
			}

			presenceUpdateTask = statusUpdateExecutor.scheduleWithFixedDelay(() -> {
				try {
					doUpdateBotPresence(enableStatus, enableActivity);
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.presence_update_failed", e.getMessage()));
				}
			}, 0, 30, TimeUnit.SECONDS);
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
			Map<String, ResponsePacket> infoMap = NetworkManager.requestInfoSnapshot(3);
			for (String client : connectedClients) {
				ResponsePacket info = infoMap.get(client);
				if (info != null && info.maxPlayerCount > 0) {
					onlinePlayerCount += info.onlinePlayerCount;
					maxPlayerCount += info.maxPlayerCount;
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
			if (presenceUpdateTask != null) {
				presenceUpdateTask.cancel(false);
				presenceUpdateTask = null;
			}
			if (statusUpdateExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(statusUpdateExecutor);
				statusUpdateExecutor = null;
			}
		}
	}
}
