package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Periodically updates configured Discord channel topics and voice channel names.
 *
 * @author Xujiayao
 */
public final class ChannelUpdateManager {

	private static final int INFO_REQUEST_TIMEOUT_SECONDS = 3;
	private static final int ONLINE_ASYNC_UPDATE_TIMEOUT_SECONDS = 10;

	private static ScheduledExecutorService channelUpdateExecutor;
	private static ScheduledFuture<?> channelUpdateTask;

	private ChannelUpdateManager() {
	}

	/**
	 * Starts the periodic channel update task.
	 */
	public static void start() {
		if (isAllUpdateDisabled()) {
			return;
		}

		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			return;
		}

		int intervalMinutes = Math.max(1, ConfigManager.getInt("channel_updating.interval_minutes", 10));
		synchronized (ChannelUpdateManager.class) {
			if (channelUpdateExecutor == null || channelUpdateExecutor.isShutdown()) {
				channelUpdateExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-ChannelUpdate"));
			}
			if (channelUpdateTask != null) {
				channelUpdateTask.cancel(false);
			}

			channelUpdateTask = channelUpdateExecutor.scheduleWithFixedDelay(() -> {
				try {
					doUpdateChannelsAsync();
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()));
				}
			}, 10, intervalMinutes * 60L, TimeUnit.SECONDS);
		}
	}

	/**
	 * For single_server shutdown flow: push an offline status update and block until requests complete.
	 */
	public static void updateOfflineForSingleServerShutdownAndWait() {
		if (!"single_server".equals(ModeManager.getMode()) || isAllUpdateDisabled()) {
			return;
		}

		try {
			doUpdateChannelsSync(buildOfflineContext());
		} catch (Exception e) {
			LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()));
		}
	}

	private static void doUpdateChannelsAsync() {
		JDA jda = DiscordManager.getJda();
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return;
		}

		ChannelUpdateContext context = collectContext();
		boolean dropWhenRateLimited = "single_server".equals(ModeManager.getMode()) && context.onlineServerCount() > 0;
		updateTextChannelTopicsAsync(context, dropWhenRateLimited);
		updateVoiceChannelNamesAsync(context, dropWhenRateLimited);
	}

	private static void doUpdateChannelsSync(ChannelUpdateContext context) {
		JDA jda = DiscordManager.getJda();
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return;
		}

		updateTextChannelTopicsSync(context);
		updateVoiceChannelNamesSync(context);
	}

	private static ChannelUpdateContext buildOfflineContext() {
		long nowEpochSeconds = Instant.now().getEpochSecond();
		return new ChannelUpdateContext(nowEpochSeconds, 0, 0, 0, 0, "", 0);
	}

	private static ChannelUpdateContext collectContext() {
		long nowEpochSeconds = Instant.now().getEpochSecond();

		List<CommandPackets.Info.ResponsePacket> onlinePackets = NetworkManager.requestInfoSnapshot(INFO_REQUEST_TIMEOUT_SECONDS)
				.values()
				.stream()
				.filter(packet -> packet != null && packet.maxPlayerCount > 0)
				.toList();

		if (onlinePackets.isEmpty()) {
			return new ChannelUpdateContext(nowEpochSeconds, 0, 0, 0, 0, "", 0);
		}

		int onlinePlayerCount = onlinePackets.stream().mapToInt(packet -> packet.onlinePlayerCount).sum();
		int maxPlayerCount = onlinePackets.stream().mapToInt(packet -> packet.maxPlayerCount).sum();
		int playersEverJoined = onlinePackets.stream().mapToInt(packet -> packet.playersEverJoined).sum();
		int onlineServerCount = onlinePackets.size();
		long maxUptimeSeconds = onlinePackets.stream().mapToLong(packet -> Math.max(0L, packet.uptimeSeconds)).max().orElse(0L);
		long serverStartedTime = Math.max(0L, nowEpochSeconds - maxUptimeSeconds);
		String onlineServerList = onlinePackets.stream()
				.map(packet -> packet.serverName == null ? "unknown" : packet.serverName)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.reduce((left, right) -> left + ", " + right)
				.orElse("");

		return new ChannelUpdateContext(
				nowEpochSeconds,
				onlinePlayerCount,
				maxPlayerCount,
				playersEverJoined,
				onlineServerCount,
				onlineServerList,
				serverStartedTime
		);
	}

	private static void updateTextChannelTopicsAsync(ChannelUpdateContext context, boolean dropWhenRateLimited) {
		if (!ConfigManager.getBoolean("channel_updating.channel_topic_updating.enable")) {
			return;
		}

		JsonNode channelsNode = ConfigManager.getConfigNode("channel_updating.channel_topic_updating.channels");
		if (!channelsNode.isArray()) {
			return;
		}

		String topic = buildTopic(context);
		if (topic.isBlank()) {
			return;
		}

		for (JsonNode node : channelsNode) {
			String identifier = node.asText("").trim();
			if (identifier.isBlank()) {
				continue;
			}
			TextChannel channel = resolveTextChannel(identifier);
			if (channel == null) {
				continue;
			}

			if (dropWhenRateLimited) {
				channel.getManager()
						.setTopic(topic)
						.timeout(ONLINE_ASYNC_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
						.queue(
								_ -> {
								},
								_ -> {
								}
						);
			} else {
				channel.getManager()
						.setTopic(topic)
						.queue(
								_ -> {
								},
								e -> LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()))
						);
			}
		}
	}

	private static void updateTextChannelTopicsSync(ChannelUpdateContext context) {
		if (!ConfigManager.getBoolean("channel_updating.channel_topic_updating.enable")) {
			return;
		}

		JsonNode channelsNode = ConfigManager.getConfigNode("channel_updating.channel_topic_updating.channels");
		if (!channelsNode.isArray()) {
			return;
		}

		String topic = buildTopic(context);
		if (topic.isBlank()) {
			return;
		}

		for (JsonNode node : channelsNode) {
			String identifier = node.asText("").trim();
			if (identifier.isBlank()) {
				continue;
			}
			TextChannel channel = resolveTextChannel(identifier);
			if (channel == null) {
				continue;
			}

			try {
				channel.getManager().setTopic(topic).complete();
			} catch (Exception e) {
				LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()));
			}
		}
	}

	private static void updateVoiceChannelNamesAsync(ChannelUpdateContext context, boolean dropWhenRateLimited) {
		if (!ConfigManager.getBoolean("channel_updating.voice_channel_updating.enable")) {
			return;
		}

		updateVoiceChannelNameAsync(
				ConfigManager.getString("channel_updating.voice_channel_updating.server_status_channel_id", ""),
				buildServerStatusChannelName(context),
				dropWhenRateLimited
		);
		updateVoiceChannelNameAsync(
				ConfigManager.getString("channel_updating.voice_channel_updating.player_count_channel_id", ""),
				buildPlayerCountChannelName(context),
				dropWhenRateLimited
		);
	}

	private static void updateVoiceChannelNamesSync(ChannelUpdateContext context) {
		if (!ConfigManager.getBoolean("channel_updating.voice_channel_updating.enable")) {
			return;
		}

		updateVoiceChannelNameSync(
				ConfigManager.getString("channel_updating.voice_channel_updating.server_status_channel_id", ""),
				buildServerStatusChannelName(context)
		);
		updateVoiceChannelNameSync(
				ConfigManager.getString("channel_updating.voice_channel_updating.player_count_channel_id", ""),
				buildPlayerCountChannelName(context)
		);
	}

	private static void updateVoiceChannelNameAsync(String channelId, String name, boolean dropWhenRateLimited) {
		if (channelId == null || channelId.isBlank() || name == null || name.isBlank()) {
			return;
		}

		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			return;
		}

		VoiceChannel channel = jda.getVoiceChannelById(channelId);
		if (channel == null) {
			return;
		}

		if (dropWhenRateLimited) {
			channel.getManager()
					.setName(name)
					.timeout(ONLINE_ASYNC_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
					.queue(
							_ -> {
							},
							_ -> {
							}
					);
		} else {
			channel.getManager()
					.setName(name)
					.queue(
							_ -> {
							},
							e -> LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()))
					);
		}
	}

	private static void updateVoiceChannelNameSync(String channelId, String name) {
		if (channelId == null || channelId.isBlank() || name == null || name.isBlank()) {
			return;
		}

		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			return;
		}

		VoiceChannel channel = jda.getVoiceChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			channel.getManager().setName(name).complete();
		} catch (Exception e) {
			LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.channel_update_failed", e.getMessage()));
		}
	}

	private static TextChannel resolveTextChannel(String identifier) {
		JDA jda = DiscordManager.getJda();
		if (jda == null) {
			return null;
		}

		List<TextChannel> channels = jda.getTextChannelsByName(identifier, true);
		if (!channels.isEmpty()) {
			return channels.getFirst();
		}

		return jda.getTextChannelById(identifier);
	}

	private static String buildTopic(ChannelUpdateContext context) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		JsonNode node = customMessages.path("channel_topic_updating");
		String template;
		if (context.onlineServerCount() == 0) {
			template = node.path("all_servers_offline").asText("");
		} else {
			template = node.path("at_least_one_server_online").path(getModeKey()).asText("");
		}

		return applyPlaceholders(template, context);
	}

	private static String buildServerStatusChannelName(ChannelUpdateContext context) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		JsonNode node = customMessages.path("voice_channels_updating").path("server_status");
		String template;
		if (context.onlineServerCount() == 0) {
			template = node.path("all_servers_offline").asText("");
		} else {
			template = node.path("at_least_one_server_online").path(getModeKey()).asText("");
		}

		return applyPlaceholders(template, context);
	}

	private static String buildPlayerCountChannelName(ChannelUpdateContext context) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		JsonNode node = customMessages.path("voice_channels_updating").path("player_count");
		String template;
		if (context.onlineServerCount() == 0) {
			template = node.path("all_servers_offline").asText("");
		} else {
			template = node.path("at_least_one_server_online").asText("");
		}

		return applyPlaceholders(template, context);
	}

	private static String applyPlaceholders(String template, ChannelUpdateContext context) {
		if (template == null || template.isBlank()) {
			return "";
		}

		return template
				.replace("{online_player_count}", String.valueOf(context.onlinePlayerCount()))
				.replace("{max_player_count}", String.valueOf(context.maxPlayerCount()))
				.replace("{players_ever_joined}", String.valueOf(context.playersEverJoined()))
				.replace("{online_server_count}", String.valueOf(context.onlineServerCount()))
				.replace("{online_server_list}", context.onlineServerList())
				.replace("{server_started_time}", String.valueOf(context.serverStartedTime()))
				.replace("{last_update_time}", String.valueOf(context.lastUpdateTime()));
	}

	private static boolean isAllUpdateDisabled() {
		return !ConfigManager.getBoolean("channel_updating.channel_topic_updating.enable")
				&& !ConfigManager.getBoolean("channel_updating.voice_channel_updating.enable");
	}

	private static String getModeKey() {
		return "standalone".equals(ModeManager.getMode()) ? "standalone" : "single_server";
	}

	static void shutdown() {
		synchronized (ChannelUpdateManager.class) {
			if (channelUpdateTask != null) {
				channelUpdateTask.cancel(false);
				channelUpdateTask = null;
			}
			if (channelUpdateExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(channelUpdateExecutor);
				channelUpdateExecutor = null;
			}
		}
	}

	private record ChannelUpdateContext(
			long lastUpdateTime,
			int onlinePlayerCount,
			int maxPlayerCount,
			int playersEverJoined,
			int onlineServerCount,
			String onlineServerList,
			long serverStartedTime
	) {
	}
}

