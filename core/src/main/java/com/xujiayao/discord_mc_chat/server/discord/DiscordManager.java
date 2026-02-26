package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Discord using JDA (Java Discord API).
 *
 * @author Xujiayao
 */
public class DiscordManager {

	private static JDA jda;

	/**
	 * Initializes the Discord bot.
	 *
	 * @return true if initialization is successful, false otherwise.
	 */
	public static boolean init() {
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
					try {
						jda = JDABuilder.createDefault(token)
								.enableIntents(
										GatewayIntent.MESSAGE_CONTENT,
										GatewayIntent.GUILD_MEMBERS
								)
								.setMemberCachePolicy(MemberCachePolicy.ALL)
								.addEventListeners(new DiscordEventHandler())
								.build();

						jda.awaitReady();
					} catch (InterruptedException e) {
						LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
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
	 * Sends a message to a Discord channel using the JDA bot.
	 *
	 * @param channelIdentifier The ID or Name of the channel.
	 * @param content           The message content.
	 */
	private static void sendBotMessage(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content).queue();
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

		List<Message.MentionType> allowedMentions = getAllowedMentions();

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(allowedMentions)
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
		JsonNode allowMentionsNode = ConfigManager.getConfigNode("discord.webhook.allow_mentions");
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
	 * @param isTemplate   Whether the message is a template.
	 * @param placeholders A map of placeholders to replace in the message.
	 */
	public static void clientBroadcast(String clientName, String channelNode, String lang, boolean isTemplate, Map<String, String> placeholders) {
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

			String[] parts = ("minecraft_to_discord." + lang).split("\\.");
			JsonNode messageNode = customMessages;
			for (String part : parts) {
				messageNode = messageNode.path(part);
			}

			if (!isTemplate) {
				String message = messageNode.asText();

				for (Map.Entry<String, String> entry : placeholders.entrySet()) {
					message = message.replace("{" + entry.getKey() + "}", entry.getValue());
				}

				if ("standalone".equals(ModeManager.getMode())) {
					String avatarUrl = getClientAvatarUrl(clientName);
					sendWebhookMessage(channel, clientName, avatarUrl, message);
				} else {
					sendBotMessage(channelIdentifier, message);
				}
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
