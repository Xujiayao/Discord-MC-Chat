package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Discord using JDA (Java Discord API).
 *
 * @author Xujiayao
 */
public class DiscordManager {

	private static final Map<String, String> DISCORD_NAME_CACHE = new ConcurrentHashMap<>();
	private static JDA jda;
	private static ScheduledExecutorService statusUpdateExecutor;
	private static ScheduledFuture<?> presenceUpdateTask;

	/**
	 * Initializes the Discord bot.
	 *
	 * @return true if initialization is successful, false otherwise.
	 */
	public static boolean init() {
		if (statusUpdateExecutor == null || statusUpdateExecutor.isShutdown()) {
			statusUpdateExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-BotPresence"));
		}

		String token = ConfigManager.getString("discord.bot.token");
		if (token.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.token_missing"));
			return false;
		}

		// Use a custom executor with our special ThreadFactory to ensure ClassLoader is correct
		try (ExecutorService executor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordInit"))) {
			try {
				// Blocks until JDA is ready
				CompletableFuture<Void> readyFuture = CompletableFuture.runAsync(() -> {
					ExecutorService eventExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordEvent"));
					ExecutorService callbackExecutor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordCallback"));
					try {
						jda = JDABuilder.createDefault(token)
								.enableIntents(
										GatewayIntent.MESSAGE_CONTENT,
										GatewayIntent.GUILD_MEMBERS,
										GatewayIntent.GUILD_MESSAGE_REACTIONS
								)
								.setMemberCachePolicy(MemberCachePolicy.ALL)
								.setEventPool(eventExecutor, true)
								.setCallbackPool(callbackExecutor, true)
								.addEventListeners(new DiscordEventHandler())
								.build();

						jda.awaitReady();
					} catch (InterruptedException e) {
						LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
					} catch (RuntimeException e) {
						// If build() fails before JDA takes ownership of executors, shut them down to avoid leaks
						eventExecutor.shutdownNow();
						callbackExecutor.shutdownNow();
						throw e;
					}
				}, executor);

				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!readyFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.waiting_ready"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				readyFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.ready", jda.getSelfUser().getAsTag()));
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
			}

			if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
				// Don't forget to shut down the temporary executor
				executor.shutdown();
				return false;
			}

			// Blocks until commands are updated
			try {
				List<CommandData> commands = new ArrayList<>();
				commands.add(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")));
				commands.add(Commands.slash("info", I18nManager.getDmccTranslation("commands.info.description")));
				commands.add(Commands.slash("log", I18nManager.getDmccTranslation("commands.log.description"))
						.addOption(OptionType.STRING, "file", I18nManager.getDmccTranslation("commands.log.args_desc.file"), true, true));
				commands.add(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")));

				if ("standalone".equals(ModeManager.getMode())) {
					commands.add(Commands.slash("console", I18nManager.getDmccTranslation("commands.console.description"))
							.addOption(OptionType.STRING, "at", I18nManager.getDmccTranslation("commands.console.args_desc.at"), true, true)
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.console.args_desc.command"), true, true));
					commands.add(Commands.slash("execute", I18nManager.getDmccTranslation("commands.execute.description"))
							.addOption(OptionType.STRING, "at", I18nManager.getDmccTranslation("commands.execute.args_desc.at"), true, true)
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.execute.args_desc.command"), true, true));
					commands.add(Commands.slash("shutdown", I18nManager.getDmccTranslation("commands.shutdown.description")));
				} else {
					// single_server mode: /console <command> (no "at" parameter)
					commands.add(Commands.slash("console", I18nManager.getDmccTranslation("commands.console.description"))
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.console.args_desc.command"), true, true));
					commands.add(Commands.slash("stats", I18nManager.getDmccTranslation("commands.stats.description"))
							.addOption(OptionType.STRING, "type", I18nManager.getDmccTranslation("commands.stats.args_desc.type"), true, true)
							.addOption(OptionType.STRING, "stat", I18nManager.getDmccTranslation("commands.stats.args_desc.stat"), true, true));
				}

				// Account linking commands (available in both standalone and single_server modes)
				commands.add(Commands.slash("link", I18nManager.getDmccTranslation("commands.link.description"))
						.addOption(OptionType.STRING, "code", I18nManager.getDmccTranslation("commands.link.args_desc.code"), true));
				commands.add(Commands.slash("unlink", I18nManager.getDmccTranslation("commands.unlink.description")));
				commands.add(Commands.slash("links", I18nManager.getDmccTranslation("commands.links.description")));

				CompletableFuture<List<Command>> updateFuture = jda.updateCommands().addCommands(commands).submit();
				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!updateFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.registering_commands"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				updateFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.commands_success"));
				return true;
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.commands_failed"), e);
			}
		}

		return false;
	}

	/**
	 * Retrieves the current Discord status info.
	 *
	 * @return The DiscordStatusInfo, or null if JDA is not ready
	 */
	public static DiscordStatusInfo getStatusInfo() {
		if (jda == null) {
			return null;
		}

		long restPing = -1;
		try {
			restPing = jda.getRestPing().complete();
		} catch (Exception ignored) {
		}

		return new DiscordStatusInfo(
				jda.getStatus().toString(),
				jda.getSelfUser().getAsTag(),
				jda.getGatewayPing(),
				restPing
		);
	}

	/**
	 * Updates the Discord bot's status and activity based on the current server state.
	 * Debounce rapid calls and automatically updates every 30 seconds.
	 */
	public static void updateBotPresence() {
		if (jda == null) return;

		boolean enableStatus = ConfigManager.getBoolean("discord.bot.enable_status");
		boolean enableActivity = ConfigManager.getBoolean("discord.bot.enable_activity");

		if (!enableStatus && !enableActivity) return;

		if (statusUpdateExecutor == null || statusUpdateExecutor.isShutdown()) return;

		synchronized (DiscordManager.class) {
			// Cancel the previously scheduled update.
			// If it is currently running, cancel(false) allows it to finish safely.
			// If it is in the queue, it is removed, naturally achieving debounce.
			if (presenceUpdateTask != null) {
				presenceUpdateTask.cancel(false);
			}

			// Schedule a new task to run immediately (0 delay), then repeat every 30 seconds.
			presenceUpdateTask = statusUpdateExecutor.scheduleWithFixedDelay(() -> {
				try {
					doUpdateBotPresence(enableStatus, enableActivity);
				} catch (Exception e) {
					LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.presence_update_failed", e.getMessage()));
				}
			}, 0, 30, TimeUnit.SECONDS);
		}
	}

	/**
	 * Internal method to perform the network fetching and JDA Presence updating.
	 */
	private static void doUpdateBotPresence(boolean enableStatus, boolean enableActivity) {
		int onlinePlayerCount = 0;
		int maxPlayerCount = 0;
		int onlineServerCount = 0;

		List<String> connectedClients = NetworkManager.getConnectedClientNames();
		if (!connectedClients.isEmpty()) {
			Map<String, InfoResponsePacket> infoMap = NetworkManager.requestInfoSnapshot(3);
			for (String client : connectedClients) {
				InfoResponsePacket info = infoMap.get(client);
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
					activityText = customMessages.path("activity").path("all_servers_offline").asText();
				} else {
					activityText = customMessages.path("activity").path("at_least_one_server_online").asText();
				}

				activityText = activityText.replace("{online_player_count}", String.valueOf(onlinePlayerCount))
						.replace("{max_player_count}", String.valueOf(maxPlayerCount));

				jda.getPresence().setActivity(Activity.playing(activityText));
			}
		}
	}

	/**
	 * Resolves a Discord username from a user ID via JDA.
	 * Results are cached in memory to avoid repeated blocking API calls.
	 * Falls back to the raw ID if JDA is not available or the user cannot be found.
	 *
	 * @param discordId The Discord user ID.
	 * @return The resolved username, or the raw ID if resolution fails.
	 */
	public static String resolveDiscordUserName(String discordId) {
		String cached = DISCORD_NAME_CACHE.get(discordId);
		if (cached != null) {
			return cached;
		}
		if (jda == null) {
			return discordId;
		}
		try {
			String name = jda.retrieveUserById(discordId).complete().getName();
			DISCORD_NAME_CACHE.put(discordId, name);
			return name;
		} catch (Exception e) {
			return discordId;
		}
	}

	/**
	 * Retrieves a Discord User object by ID.
	 *
	 * @param discordId The Discord user ID.
	 * @return The User object, or null if JDA is not available or user not found.
	 */
	public static User retrieveUser(String discordId) {
		if (jda == null) return null;
		try {
			return jda.retrieveUserById(discordId).complete();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Retrieves a Discord Member object by user ID from the first mutual guild.
	 *
	 * @param discordId The Discord user ID.
	 * @return The Member object, or null if JDA is not available or member not found.
	 */
	public static Member retrieveMember(String discordId) {
		if (jda == null) return null;
		for (var guild : jda.getGuilds()) {
			try {
				Member member = guild.retrieveMemberById(discordId).complete();
				if (member != null) {
					return member;
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	/**
	 * Sends a message to a Discord channel using the JDA bot.
	 *
	 * @param channelIdentifier The ID or Name of the channel.
	 * @param content           The message content.
	 */
	private static void sendBotMessage(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(getAllowedMentions())
					.queue();
		}
	}

	/**
	 * Sends a message using a Discord webhook. Uses allowed_mention specified in config.
	 *
	 * @param channel   The target channel.
	 * @param username  The username to display.
	 * @param avatarUrl The avatar URL to use.
	 * @param content   The message content.
	 */
	private static void sendWebhookMessage(TextChannel channel, String username, String avatarUrl, String content) {
		// Find or create webhook
		Webhook webhook = getOrCreateWebhook(channel);

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(getAllowedMentions())
				.queue();
	}

	/**
	 * Sends a message with a file attachment using a Discord webhook.
	 *
	 * @param channel   The target channel.
	 * @param username  The username to display.
	 * @param avatarUrl The avatar URL to use.
	 * @param content   The message content.
	 * @param fileData  The file data.
	 * @param fileName  The file name.
	 */
	private static void sendWebhookMessageWithFile(TextChannel channel, String username, String avatarUrl,
												   String content, byte[] fileData, String fileName) {
		Webhook webhook = getOrCreateWebhook(channel);

		List<Message.MentionType> allowedMentions = getAllowedMentions();

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(allowedMentions)
				.addFiles(FileUpload.fromData(fileData, fileName))
				.queue();
	}

	/**
	 * Gets or creates the DMCC webhook for a channel.
	 *
	 * @param channel The target channel
	 * @return The webhook
	 */
	private static Webhook getOrCreateWebhook(TextChannel channel) {
		return channel.retrieveWebhooks().complete()
				.stream()
				.filter(i -> "DMCC Webhook".equals(i.getName()))
				.filter(i -> i.getOwnerAsUser() == jda.getSelfUser())
				.findFirst()
				.orElseGet(() -> channel.createWebhook("DMCC Webhook").complete()); // Must use orElseGet to avoid unnecessary creation
	}

	/**
	 * Gets the allowed mention types from config.
	 *
	 * @return The list of allowed mention types
	 */
	private static List<Message.MentionType> getAllowedMentions() {
		List<Message.MentionType> allowedMentions = new ArrayList<>();
		JsonNode allowMentionsNode = ConfigManager.getConfigNode("discord.allow_mentions");
		if (allowMentionsNode.isArray()) {
			for (JsonNode node : allowMentionsNode) {
				switch (node.asText()) {
					case "everyone" -> {
						allowedMentions.add(Message.MentionType.EVERYONE);
						allowedMentions.add(Message.MentionType.HERE);
					}
					case "users" -> allowedMentions.add(Message.MentionType.USER);
					case "roles" -> allowedMentions.add(Message.MentionType.ROLE);
				}
			}
		}
		return allowedMentions;
	}

	/**
	 * Gets the avatar URL for a given client server name from config.
	 *
	 * @param clientName The client server name
	 * @return The avatar URL, or the bot's own avatar URL if not configured
	 */
	private static String getClientAvatarUrl(String clientName) {
		String avatarUrl = "";
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode != null && serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (clientName.equals(node.path("name").asText())) {
					avatarUrl = node.path("avatar_url").asText();
				}
			}
		}
		if (avatarUrl == null || avatarUrl.isBlank()) {
			avatarUrl = jda.getSelfUser().getEffectiveAvatarUrl();
		}
		return avatarUrl;
	}

	/**
	 * Sends an execute command result via webhook, using the client's server name as the webhook username.
	 * The result is sent to the in-game-chat channel (same as server events).
	 *
	 * @param clientName The name of the DMCC client
	 * @param message    The result message
	 */
	public static void sendExecuteResultViaWebhook(String clientName, String message) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord.server.started", "in-game-chat");
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = getClientAvatarUrl(clientName);
			sendWebhookMessage(channel, clientName, avatarUrl, "```\n" + message + "\n```");
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Sends an execute command result with a file attachment via webhook.
	 *
	 * @param clientName The name of the DMCC client
	 * @param message    The result message
	 * @param fileData   The file data
	 * @param fileName   The file name
	 */
	public static void sendExecuteResultWithFileViaWebhook(String clientName, String message,
														   byte[] fileData, String fileName) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord.server.started", "in-game-chat");
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = getClientAvatarUrl(clientName);
			sendWebhookMessageWithFile(channel, clientName, avatarUrl,
					"```\n" + message + "\n```", fileData, fileName);
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Broadcasts a message to a Discord channel based on the specified parameters.
	 *
	 * @param clientName   The name of the DMCC client.
	 * @param channelNode  The broadcast channel identifier.
	 * @param lang         The language key for the message.
	 * @param placeholders A map of placeholders to replace in the message.
	 */
	public static void clientBroadcast(String clientName, String channelNode, String lang, Map<String, String> placeholders) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			// User chooses not to broadcast this event
			return;
		}
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			JsonNode customMessages = I18nManager.getCustomMessages();
			if (customMessages == null) return;

			String[] parts = ("minecraft_to_xxxxx." + lang).split("\\.");
			JsonNode messageNode = customMessages;
			for (String part : parts) {
				messageNode = messageNode.path(part);
			}

			String message = messageNode.asText();

			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}

			if ("standalone".equals(ModeManager.getMode())) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, message);

				for (String line : message.split("\n")) {
					// Escape underscores in :emoji: to prevent being treated as Markdown formatting
					line = Pattern.compile("(:[^:]+:)").matcher(line)
							.replaceAll(m -> m.group().replace("_", "\\\\_"));
					line = MarkdownSanitizer.sanitize(line).replace("\\_", "_");

					LOGGER.info(StringUtils.format("[{}] {}"), clientName, line);
				}
			} else {
				sendBotMessage(channelIdentifier, message);
			}
		} catch (InsufficientPermissionException e) {
			String reason = I18nManager.getDmccTranslation("discord.manager.insufficient_permission", channel.getName(), e.getPermission().getName());
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", reason));
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Retrieves a TextChannel by its ID or name.
	 *
	 * @param identifier The ID or name of the channel.
	 * @return The TextChannel if found, null otherwise.
	 */
	private static TextChannel getTextChannel(String identifier) {
		TextChannel tc;

		// Try search by name
		// Return first result. Use with caution if multiple channels have the same name.
		List<TextChannel> channels = jda.getTextChannelsByName(identifier, true);
		if (!channels.isEmpty()) {
			tc = channels.getFirst();
		} else {
			// Try parsing as ID
			tc = jda.getTextChannelById(identifier);
			if (tc == null) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
				return null;
			}
		}

		if (!tc.canTalk()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_cannot_talk", identifier));
			return null;
		}

		return tc;
	}

	/**
	 * Shuts down the Discord bot.
	 */
	public static void shutdown() {
		if (statusUpdateExecutor != null) {
			ExecutorServiceUtils.shutdownAnExecutor(statusUpdateExecutor);
			statusUpdateExecutor = null;
		}

		if (jda != null) {
			jda.shutdown();
			try {
				if (ConfigManager.getBoolean("shutdown.graceful_shutdown")) {
					// Allow up to 10 minutes for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofMinutes(10));
				} else {
					// Allow up to 5 seconds for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofSeconds(5));
				}
			} catch (Exception ignored) {
			}
			jda.shutdownNow();

			jda = null;
		}
	}

	/**
	 * Data holder for Discord status info.
	 *
	 * @param status            JDA status string
	 * @param tag               Bot user tag
	 * @param gatewayPingMillis Gateway ping in milliseconds
	 * @param restPingMillis    REST ping in milliseconds
	 */
	public record DiscordStatusInfo(String status, String tag, long gatewayPingMillis, long restPingMillis) {
	}
}
