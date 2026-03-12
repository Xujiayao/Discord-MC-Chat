package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordMessagePacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
import java.util.concurrent.ConcurrentHashMap;
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

	private static final Map<String, String> DISCORD_NAME_CACHE = new ConcurrentHashMap<>();
	private static final int MAX_REPLY_PREVIEW_LENGTH = 50;

	/**
	 * Pre-compiled regex pattern for parsing Discord raw message content.
	 * Matches URLs, user/role mentions, @everyone/@here, custom emojis, and markdown formatting.
	 */
	private static final java.util.regex.Pattern DISCORD_MESSAGE_PATTERN = java.util.regex.Pattern.compile(
			"(https?://\\S+)" +                                         // Group 1: URLs
			"|(<@!?(\\d+)>)" +                                           // Group 2,3: User mentions
			"|(<@&(\\d+)>)" +                                            // Group 4,5: Role mentions
			"|(@everyone|@here)" +                                       // Group 6: @everyone and @here
			"|(<a?:(\\w+):(\\d+)>)" +                                    // Group 7,8,9: Custom emojis
			"|(<t:(\\d+)(?::([tTdDfFR]))?>)" +                           // Group 10,11,12: Discord timestamps
			"|(<#(\\d+)>)" +                                             // Group 13,14: Channel mentions
			"|(```(?:\\w*\\n)?([\\s\\S]*?)```)" +                        // Group 15,16: Code blocks
			"|(`([^`]+?)`)" +                                            // Group 17,18: Inline code
			"|(\\[([^\\]]+)]\\((https?://\\S+?)\\))" +                   // Group 19,20,21: Masked links
			"|(\\*\\*\\*(.+?)\\*\\*\\*)" +                               // Group 22,23: Bold italic
			"|(\\*\\*(.+?)\\*\\*)" +                                     // Group 24,25: Bold
			"|(\\*(.+?)\\*)" +                                           // Group 26,27: Italic
			"|(__(.+?)__)" +                                             // Group 28,29: Underline
			"|(~~(.+?)~~)" +                                             // Group 30,31: Strikethrough
			"|(\\|\\|(.+?)\\|\\|)" +                                     // Group 32,33: Spoiler
			"|(\\\\([*_~`|]))"                                           // Group 34,35: Escaped chars
	);

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
	public static net.dv8tion.jda.api.entities.User retrieveUser(String discordId) {
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
	public static net.dv8tion.jda.api.entities.Member retrieveMember(String discordId) {
		if (jda == null) return null;
		for (var guild : jda.getGuilds()) {
			try {
				net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(discordId).complete();
				if (member != null) {
					return member;
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	/**
	 * Resolves a Discord channel name from its ID.
	 *
	 * @param channelId The Discord channel ID.
	 * @return The channel name, or the raw ID if not found.
	 */
	private static String resolveChannelName(String channelId) {
		if (jda == null) return channelId;
		try {
			TextChannel channel = jda.getTextChannelById(channelId);
			if (channel != null) {
				return channel.getName();
			}
		} catch (Exception ignored) {
		}
		return channelId;
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

			if (isTemplate) {
				// Template-based message (e.g., player chat/command, /say, /tellraw)
				String templateName = messageNode.asText();
				sendTemplateMessage(channel, clientName, templateName, placeholders);
			} else {
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
	 * Sends a template-based message to Discord, resolving the template from custom_messages.
	 * Supports both webhook (fake user) and bot message modes.
	 *
	 * @param channel      The target Discord channel
	 * @param clientName   The DMCC client name
	 * @param templateName The template name (e.g., "default")
	 * @param placeholders The placeholders to replace
	 */
	private static void sendTemplateMessage(TextChannel channel, String clientName, String templateName,
											Map<String, String> placeholders) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) return;

		// Look up the template by name
		JsonNode templatesNode = customMessages.path("templates");
		JsonNode selectedTemplate = null;
		if (templatesNode.isArray()) {
			for (JsonNode template : templatesNode) {
				if (templateName.equals(template.path("name").asText())) {
					selectedTemplate = template;
					break;
				}
			}
		}
		if (selectedTemplate == null) return;

		String mode = ModeManager.getMode();
		String modeKey = "standalone".equals(mode) ? "standalone" : "single_server";

		boolean useWebhook = ConfigManager.getBoolean("discord.webhook.players.enable_fake_user_style") == Boolean.TRUE;

		// Apply MC→Discord display formatting to the message content
		Map<String, String> formattedPlaceholders = new java.util.HashMap<>(placeholders);
		String message = formattedPlaceholders.get("message");
		if (message != null) {
			formattedPlaceholders.put("message", applyMinecraftToDiscordFormatting(message));
		}

		if (useWebhook) {
			JsonNode webhookNode = selectedTemplate.path("with_webhook").path(modeKey);
			String username = replacePlaceholders(webhookNode.path("username").asText(""), formattedPlaceholders);
			String content = replacePlaceholders(webhookNode.path("content").asText(""), formattedPlaceholders);

			// Determine avatar URL
			String avatarUrl = ConfigManager.getString("discord.webhook.players.avatar_url", "");
			avatarUrl = replacePlaceholders(avatarUrl, formattedPlaceholders);

			// If discord_user_avatar_for_webhooks is enabled and user is linked, use Discord avatar
			if (ConfigManager.getBoolean("account_linking.discord_user_avatar_for_webhooks") == Boolean.TRUE) {
				String playerUuid = formattedPlaceholders.get("player_uuid");
				if (playerUuid != null) {
					String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(playerUuid);
					if (discordId != null) {
						net.dv8tion.jda.api.entities.User discordUser = retrieveUser(discordId);
						if (discordUser != null) {
							avatarUrl = discordUser.getEffectiveAvatarUrl();
						}
					}
				}
			}

			sendWebhookMessage(channel, username, avatarUrl, content);
		} else {
			JsonNode noWebhookNode = selectedTemplate.path("without_webhook").path(modeKey);
			String formattedMessage = replacePlaceholders(noWebhookNode.asText(""), formattedPlaceholders);

			if ("standalone".equals(mode)) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, formattedMessage);
			} else {
				channel.sendMessage(formattedMessage).queue();
			}
		}
	}

	/**
	 * Replaces placeholders in a string using a map of key-value pairs.
	 *
	 * @param text         The template string with {key} placeholders
	 * @param placeholders The placeholder values
	 * @return The string with placeholders replaced
	 */
	private static String replacePlaceholders(String text, Map<String, String> placeholders) {
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			text = text.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return text;
	}

	/**
	 * Processes an incoming Discord message and forwards it to Minecraft clients.
	 * <p>
	 * Checks if the message is from a chat channel and if Discord-to-Minecraft chat
	 * forwarding is enabled. Parses the Discord message into rich TextParts that
	 * preserve markdown formatting, clickable URLs, colored mentions, and custom emojis,
	 * then broadcasts a DiscordMessagePacket to all clients.
	 *
	 * @param event The Discord message received event
	 */
	public static void processIncomingDiscordMessage(MessageReceivedEvent event) {
		// Check if Discord-to-Minecraft chat is enabled
		if (ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat") != Boolean.TRUE) {
			return;
		}

		// Check if the message is from an in-game-chat channel
		String chatChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "");
		if (chatChannel.isBlank()) {
			return;
		}

		TextChannel messageChannel = event.getChannel().asTextChannel();
		TextChannel targetChannel = getTextChannel(chatChannel);
		if (targetChannel == null || !messageChannel.getId().equals(targetChannel.getId())) {
			return;
		}

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) return;

		// Extract message info
		Member member = event.getMember();
		String senderName = member != null ? member.getEffectiveName() : event.getAuthor().getName();

		// Resolve role color (respecting use_role_colors_in_chat config)
		String roleColor = "white";
		if (ConfigManager.getBoolean("account_linking.use_role_colors_in_chat") == Boolean.TRUE && member != null) {
			java.awt.Color color = member.getColor();
			if (color != null) {
				roleColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
			}
		}

		// Read display formatting options
		boolean showMarkdown = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.markdown") == Boolean.TRUE;
		boolean showAttachments = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.attachments") == Boolean.TRUE;
		boolean showStickers = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.stickers") == Boolean.TRUE;
		boolean showUnicodeEmojis = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.unicode_emojis") == Boolean.TRUE;
		boolean showCustomEmojis = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.custom_emojis") == Boolean.TRUE;
		boolean showMentions = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.mentions") == Boolean.TRUE;
		boolean showHyperlinks = ConfigManager.getBoolean("display_formatting.discord_to_minecraft.hyperlinks") == Boolean.TRUE;

		String openUrlTooltip = I18nManager.getDmccTranslation("linking.tooltip.open_url");

		// Parse the raw message content into rich TextParts
		String rawContent = event.getMessage().getContentRaw();
		List<DiscordMessagePacket.TextPart> messageParts = parseDiscordMessageToTextParts(
				rawContent, event, showMarkdown, showCustomEmojis, showMentions,
				showHyperlinks, showUnicodeEmojis, openUrlTooltip);

		// Append attachments
		if (showAttachments) {
			for (Message.Attachment attachment : event.getMessage().getAttachments()) {
				String url = attachment.getUrl();
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(
						" [" + attachment.getFileName() + "]", false, "blue");
				part.underlined = true;
				part.clickAction = "open_url";
				part.clickValue = url;
				part.hoverText = openUrlTooltip;
				messageParts.add(part);
			}
		}

		// Append stickers
		if (showStickers) {
			for (StickerItem sticker : event.getMessage().getStickers()) {
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(
						" [Sticker: " + sticker.getName() + "]", false, "yellow");
				messageParts.add(part);
			}
		}

		if (messageParts.isEmpty()) return;

		// Determine server color for standalone mode
		String serverColor = "blue"; // Discord brand color
		String serverName = "Discord";

		// Build prefix parts from common.chat format, but replace {message} with our rich parts
		JsonNode chatFormat = customMessages.path("common").path("chat");
		List<DiscordMessagePacket.TextPart> mainParts = buildRichMessageParts(chatFormat, Map.of(
				"server", serverName,
				"server_color", serverColor,
				"name", senderName,
				"role_color", roleColor
		), messageParts);

		// Handle reply formatting
		List<DiscordMessagePacket.TextPart> replyParts = null;
		Message referencedMessage = event.getMessage().getReferencedMessage();
		if (referencedMessage != null) {
			String replyName = referencedMessage.getAuthor().getName();
			String replyRoleColor = "white";
			if (ConfigManager.getBoolean("account_linking.use_role_colors_in_chat") == Boolean.TRUE && referencedMessage.getMember() != null) {
				java.awt.Color replyColor = referencedMessage.getMember().getColor();
				if (replyColor != null) {
					replyRoleColor = String.format("#%02x%02x%02x", replyColor.getRed(), replyColor.getGreen(), replyColor.getBlue());
				}
			}
			String replyContent = referencedMessage.getContentDisplay();
			if (replyContent.length() > MAX_REPLY_PREVIEW_LENGTH) {
				replyContent = replyContent.substring(0, MAX_REPLY_PREVIEW_LENGTH) + "...";
			}

			JsonNode responseFormat = customMessages.path("discord_to_minecraft").path("response");
			replyParts = buildTextParts(responseFormat, Map.of(
					"name", replyName,
					"role_color", replyRoleColor,
					"message", replyContent
			));
		}

		// Resolve mention notifications
		List<String> mentionedPlayerUuids = new ArrayList<>();
		String mentionStyle = ConfigManager.getString("account_linking.discord_mention_notifications.style", "action_bar");
		boolean mentionEnabled = ConfigManager.getBoolean("account_linking.discord_mention_notifications.enable") == Boolean.TRUE;

		if (mentionEnabled) {
			// Check Discord @mentions for linked MC players
			for (net.dv8tion.jda.api.entities.User mentioned : event.getMessage().getMentions().getUsers()) {
				List<String> uuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(mentioned.getId());
				mentionedPlayerUuids.addAll(uuids);
			}
		}

		// Send packet to all connected clients
		DiscordMessagePacket packet = new DiscordMessagePacket(
				mainParts, replyParts,
				mentionedPlayerUuids.isEmpty() ? null : mentionedPlayerUuids,
				mentionStyle,
				senderName
		);

		NetworkManager.broadcastToClients(packet);
	}

	/**
	 * Broadcasts a Discord command execution notification to Minecraft clients.
	 *
	 * @param commandName The name of the command executed
	 * @param senderName  The name of the Discord user
	 * @param roleColor   The role color of the Discord user (hex or Minecraft color name)
	 */
	public static void broadcastCommandExecutionToMinecraft(String commandName, String senderName, String roleColor) {
		if (ConfigManager.getBoolean("broadcasts.discord_to_minecraft.command") != Boolean.TRUE) {
			return;
		}

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) return;

		JsonNode commandFormat = customMessages.path("discord_to_minecraft").path("command");
		List<DiscordMessagePacket.TextPart> parts = buildTextParts(commandFormat, Map.of(
				"name", senderName,
				"role_color", roleColor,
				"command", commandName
		));

		DiscordMessagePacket packet = new DiscordMessagePacket(parts, null, null, null, null);
		NetworkManager.broadcastToClients(packet);
	}

	/**
	 * Builds a list of TextPart objects from a custom_messages format node.
	 * Each entry in the node should have "text", "bold", and "color" fields.
	 *
	 * @param formatNode   The JsonNode array containing format entries
	 * @param placeholders The placeholders to replace in text fields
	 * @return A list of TextPart objects
	 */
	private static List<DiscordMessagePacket.TextPart> buildTextParts(JsonNode formatNode, Map<String, String> placeholders) {
		List<DiscordMessagePacket.TextPart> parts = new ArrayList<>();
		if (formatNode == null || !formatNode.isArray()) return parts;

		for (JsonNode entry : formatNode) {
			String text = entry.path("text").asText("");
			boolean bold = entry.path("bold").asBoolean(false);
			String color = entry.path("color").asText("white");

			// Replace placeholders in text and color
			text = replacePlaceholders(text, placeholders);
			color = replacePlaceholders(color, placeholders);

			parts.add(new DiscordMessagePacket.TextPart(text, bold, color));
		}

		return parts;
	}

	/**
	 * Builds TextParts from the common.chat format, inserting rich message parts in place of {message}.
	 * Parts of the template that contain {message} are replaced by the provided message parts,
	 * while other parts (prefix like "[Discord] " and "<Name> ") are kept from the template.
	 *
	 * @param formatNode   The JsonNode array containing format entries
	 * @param placeholders Simple placeholder values (excluding "message")
	 * @param messageParts Rich TextParts for the message content
	 * @return A combined list of TextPart objects
	 */
	private static List<DiscordMessagePacket.TextPart> buildRichMessageParts(
			JsonNode formatNode, Map<String, String> placeholders,
			List<DiscordMessagePacket.TextPart> messageParts) {
		List<DiscordMessagePacket.TextPart> parts = new ArrayList<>();
		if (formatNode == null || !formatNode.isArray()) return parts;

		for (JsonNode entry : formatNode) {
			String text = entry.path("text").asText("");
			boolean bold = entry.path("bold").asBoolean(false);
			String color = entry.path("color").asText("white");

			// Replace non-message placeholders in text and color
			text = replacePlaceholders(text, placeholders);
			color = replacePlaceholders(color, placeholders);

			if (text.contains("{message}")) {
				// Split around {message} and insert the rich message parts
				String[] segments = text.split("\\{message}", -1);
				for (int i = 0; i < segments.length; i++) {
					if (!segments[i].isEmpty()) {
						parts.add(new DiscordMessagePacket.TextPart(segments[i], bold, color));
					}
					if (i < segments.length - 1) {
						// Insert rich message parts
						parts.addAll(messageParts);
					}
				}
			} else {
				parts.add(new DiscordMessagePacket.TextPart(text, bold, color));
			}
		}

		return parts;
	}

	/**
	 * Parses a raw Discord message into rich TextParts with proper formatting.
	 * Handles markdown (bold/italic/underline/strikethrough/spoiler), mentions (@user/@role/@everyone/@here/#channel),
	 * custom emojis (:name:), URLs (clickable, blue, underlined), Tenor GIFs, inline code, code blocks,
	 * masked links, and Discord timestamps.
	 *
	 * @param rawContent      The raw message content from Discord
	 * @param event           The message event (for resolving mentions)
	 * @param showMarkdown    Whether to apply markdown formatting
	 * @param showCustomEmojis Whether to show custom emojis
	 * @param showMentions    Whether to show mentions
	 * @param showHyperlinks  Whether to show hyperlinks
	 * @param showUnicodeEmojis Whether to show unicode emojis
	 * @param openUrlTooltip  Localized "Open URL" tooltip text
	 * @return List of rich TextParts
	 */
	private static List<DiscordMessagePacket.TextPart> parseDiscordMessageToTextParts(
			String rawContent, MessageReceivedEvent event,
			boolean showMarkdown, boolean showCustomEmojis, boolean showMentions,
			boolean showHyperlinks, boolean showUnicodeEmojis, String openUrlTooltip) {

		List<DiscordMessagePacket.TextPart> parts = new ArrayList<>();
		if (rawContent == null || rawContent.isEmpty()) return parts;

		// Strip Unicode emojis if disabled
		if (!showUnicodeEmojis) {
			rawContent = stripUnicodeEmojis(rawContent);
		}

		java.util.regex.Matcher matcher = DISCORD_MESSAGE_PATTERN.matcher(rawContent);

		int lastEnd = 0;
		while (matcher.find()) {
			// Add plain text before this match
			if (matcher.start() > lastEnd) {
				String plainText = rawContent.substring(lastEnd, matcher.start());
				parts.add(new DiscordMessagePacket.TextPart(plainText, false, "gray"));
			}

			if (matcher.group(1) != null) {
				// URL - already guaranteed to be http:// or https:// by the regex
				String url = matcher.group(1);
				if (showHyperlinks) {
					// Detect Tenor GIF links by checking host
					boolean isTenorGif = false;
					try {
						java.net.URI uri = new java.net.URI(url);
						String host = uri.getHost();
						if (host != null && (host.equals("tenor.com") || host.endsWith(".tenor.com"))) {
							String path = uri.getPath();
							if (path != null && (path.startsWith("/view/") || path.startsWith("/gif/"))) {
								isTenorGif = true;
							}
						}
					} catch (Exception ignored) {
					}
					String displayText = isTenorGif ? "<gif>" : url;

					DiscordMessagePacket.TextPart urlPart = new DiscordMessagePacket.TextPart(displayText, false, "blue");
					urlPart.underlined = true;
					urlPart.clickAction = "open_url";
					urlPart.clickValue = url;
					urlPart.hoverText = openUrlTooltip;
					parts.add(urlPart);
				} else {
					parts.add(new DiscordMessagePacket.TextPart("[link]", false, "gray"));
				}
			} else if (matcher.group(2) != null) {
				// User mention <@123456> or <@!123456>
				String userId = matcher.group(3);
				if (showMentions) {
					// Resolve user name and role color
					String userName = userId;
					String mentionColor = "white";
					try {
						Member mentionedMember = event.getGuild().getMemberById(userId);
						if (mentionedMember != null) {
							userName = mentionedMember.getEffectiveName();
							if (ConfigManager.getBoolean("account_linking.use_role_colors_in_chat") == Boolean.TRUE) {
								java.awt.Color color = mentionedMember.getColor();
								if (color != null) {
									mentionColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
								}
							}
						} else {
							net.dv8tion.jda.api.entities.User user = retrieveUser(userId);
							if (user != null) {
								userName = user.getName();
							}
						}
					} catch (Exception ignored) {
					}
					parts.add(new DiscordMessagePacket.TextPart("[@" + userName + "]", false, mentionColor));
				} else {
					parts.add(new DiscordMessagePacket.TextPart("@" + userId, false, "gray"));
				}
			} else if (matcher.group(4) != null) {
				// Role mention <@&123456>
				String roleId = matcher.group(5);
				if (showMentions) {
					String roleName = roleId;
					String mentionColor = "white";
					try {
						Role role = event.getGuild().getRoleById(roleId);
						if (role != null) {
							roleName = role.getName();
							java.awt.Color color = role.getColor();
							if (color != null && ConfigManager.getBoolean("account_linking.use_role_colors_in_chat") == Boolean.TRUE) {
								mentionColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
							}
						}
					} catch (Exception ignored) {
					}
					parts.add(new DiscordMessagePacket.TextPart("[@" + roleName + "]", false, mentionColor));
				} else {
					parts.add(new DiscordMessagePacket.TextPart("@" + roleId, false, "gray"));
				}
			} else if (matcher.group(6) != null) {
				// @everyone or @here
				String mention = matcher.group(6);
				if (showMentions) {
					parts.add(new DiscordMessagePacket.TextPart("[" + mention + "]", false, "yellow"));
				} else {
					parts.add(new DiscordMessagePacket.TextPart(mention, false, "gray"));
				}
			} else if (matcher.group(7) != null) {
				// Custom emoji <:name:id> or <a:name:id>
				String emojiName = matcher.group(8);
				if (showCustomEmojis) {
					parts.add(new DiscordMessagePacket.TextPart(":" + emojiName + ":", false, "yellow"));
				}
				// If not showing custom emojis, strip it (don't add anything)
			} else if (matcher.group(10) != null) {
				// Discord timestamp <t:1234567890> or <t:1234567890:R>
				try {
					long epochSeconds = Long.parseLong(matcher.group(11));
					String style = matcher.group(12); // may be null
					String formatted = formatDiscordTimestamp(epochSeconds, style);
					parts.add(new DiscordMessagePacket.TextPart(formatted, false, "gray"));
				} catch (Exception ignored) {
					// If parsing fails, show the raw text
					parts.add(new DiscordMessagePacket.TextPart(matcher.group(10), false, "gray"));
				}
			} else if (matcher.group(13) != null) {
				// Channel mention <#channel_id>
				String channelId = matcher.group(14);
				if (showMentions) {
					String channelName = resolveChannelName(channelId);
					parts.add(new DiscordMessagePacket.TextPart("[#" + channelName + "]", false, "white"));
				} else {
					parts.add(new DiscordMessagePacket.TextPart("#" + channelId, false, "gray"));
				}
			} else if (matcher.group(15) != null && showMarkdown) {
				// Code block ```code```
				String code = matcher.group(16);
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(code, false, "dark_gray");
				parts.add(part);
			} else if (matcher.group(17) != null && showMarkdown) {
				// Inline code `code`
				String code = matcher.group(18);
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(code, false, "dark_gray");
				parts.add(part);
			} else if (matcher.group(19) != null) {
				// Masked link [text](url)
				String linkText = matcher.group(20);
				String linkUrl = matcher.group(21);
				if (showHyperlinks) {
					DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(linkText, false, "blue");
					part.underlined = true;
					part.clickAction = "open_url";
					part.clickValue = linkUrl;
					part.hoverText = openUrlTooltip;
					parts.add(part);
				} else {
					parts.add(new DiscordMessagePacket.TextPart(linkText, false, "gray"));
				}
			} else if (matcher.group(22) != null && showMarkdown) {
				// Bold italic ***text***
				String innerText = matcher.group(23);
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(innerText, true, "gray");
				part.italic = true;
				parts.add(part);
			} else if (matcher.group(24) != null && showMarkdown) {
				// Bold **text**
				parts.add(new DiscordMessagePacket.TextPart(matcher.group(25), true, "gray"));
			} else if (matcher.group(26) != null && showMarkdown) {
				// Italic *text*
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(matcher.group(27), false, "gray");
				part.italic = true;
				parts.add(part);
			} else if (matcher.group(28) != null && showMarkdown) {
				// Underline __text__
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(matcher.group(29), false, "gray");
				part.underlined = true;
				parts.add(part);
			} else if (matcher.group(30) != null && showMarkdown) {
				// Strikethrough ~~text~~
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(matcher.group(31), false, "gray");
				part.strikethrough = true;
				parts.add(part);
			} else if (matcher.group(32) != null && showMarkdown) {
				// Spoiler ||text|| - show as obfuscated text with hover to reveal
				String spoilerText = matcher.group(33);
				DiscordMessagePacket.TextPart part = new DiscordMessagePacket.TextPart(spoilerText, false, "dark_gray");
				part.obfuscated = true;
				part.hoverText = spoilerText;
				parts.add(part);
			} else if (matcher.group(34) != null) {
				// Escaped character \* \_ \~ \` \|
				parts.add(new DiscordMessagePacket.TextPart(matcher.group(35), false, "gray"));
			} else {
				// If markdown is disabled, just show the raw matched text
				parts.add(new DiscordMessagePacket.TextPart(matcher.group(), false, "gray"));
			}

			lastEnd = matcher.end();
		}

		// Add remaining plain text
		if (lastEnd < rawContent.length()) {
			String remaining = rawContent.substring(lastEnd);
			parts.add(new DiscordMessagePacket.TextPart(remaining, false, "gray"));
		}

		return parts;
	}

	/**
	 * Applies Minecraft-to-Discord display formatting options to a message string.
	 * Handles markdown escaping, mention conversion, emoji processing, and hyperlinks.
	 *
	 * @param message The raw message from Minecraft
	 * @return The formatted message suitable for Discord
	 */
	static String applyMinecraftToDiscordFormatting(String message) {
		if (message == null) return "";

		boolean useMarkdown = ConfigManager.getBoolean("display_formatting.minecraft_to_discord.markdown") == Boolean.TRUE;
		boolean useUnicodeEmojis = ConfigManager.getBoolean("display_formatting.minecraft_to_discord.unicode_emojis") == Boolean.TRUE;
		boolean useCustomEmojis = ConfigManager.getBoolean("display_formatting.minecraft_to_discord.custom_emojis") == Boolean.TRUE;
		boolean useMentions = ConfigManager.getBoolean("display_formatting.minecraft_to_discord.mentions") == Boolean.TRUE;
		boolean useHyperlinks = ConfigManager.getBoolean("display_formatting.minecraft_to_discord.hyperlinks") == Boolean.TRUE;

		String result = message;

		// Escape markdown if disabled
		if (!useMarkdown) {
			result = result.replace("*", "\\*")
					.replace("_", "\\_")
					.replace("~", "\\~")
					.replace("`", "\\`")
					.replace("|", "\\|");
		}

		// Strip Unicode emojis if disabled
		if (!useUnicodeEmojis) {
			result = stripUnicodeEmojis(result);
		}

		// Strip :emoji: patterns if custom emojis disabled
		if (!useCustomEmojis) {
			result = result.replaceAll(":\\w+:", "");
		}

		// Convert @mentions to plain text if disabled
		if (!useMentions) {
			result = result.replaceAll("@(\\S+)", "$1");
		}

		// Wrap URLs in <> to suppress Discord embed if hyperlinks disabled
		if (!useHyperlinks) {
			result = result.replaceAll("(https?://\\S+)", "<$1>");
		}

		return result;
	}

	/**
	 * Strips common Discord markdown formatting characters from a string.
	 *
	 * @param text The text with potential markdown
	 * @return The text without markdown formatting
	 */
	private static String stripMarkdown(String text) {
		if (text == null) return "";
		return text.replaceAll("\\*{1,3}(.+?)\\*{1,3}", "$1")    // bold/italic
				.replaceAll("__(.+?)__", "$1")                     // underline
				.replaceAll("~~(.+?)~~", "$1")                     // strikethrough
				.replaceAll("`{1,3}([^`]+)`{1,3}", "$1")          // inline code/code blocks
				.replaceAll("\\|{2}(.+?)\\|{2}", "$1");           // spoiler
	}

	/**
	 * Strips Unicode emoji characters from a string.
	 * Removes characters in common emoji Unicode ranges.
	 *
	 * @param text The text with potential Unicode emojis
	 * @return The text without Unicode emojis
	 */
	private static String stripUnicodeEmojis(String text) {
		if (text == null) return "";
		// Remove common Unicode emoji ranges
		return text.replaceAll("[\\x{1F600}-\\x{1F64F}]", "")   // Emoticons
				.replaceAll("[\\x{1F300}-\\x{1F5FF}]", "")       // Misc Symbols and Pictographs
				.replaceAll("[\\x{1F680}-\\x{1F6FF}]", "")       // Transport and Map
				.replaceAll("[\\x{1F1E0}-\\x{1F1FF}]", "")       // Flags
				.replaceAll("[\\x{2600}-\\x{26FF}]", "")         // Misc symbols
				.replaceAll("[\\x{2700}-\\x{27BF}]", "")         // Dingbats
				.replaceAll("[\\x{FE00}-\\x{FE0F}]", "")         // Variation Selectors
				.replaceAll("[\\x{1F900}-\\x{1F9FF}]", "")       // Supplemental Symbols and Pictographs
				.replaceAll("[\\x{200D}]", "")                    // Zero Width Joiner
				.replaceAll("[\\x{20E3}]", "")                    // Combining Enclosing Keycap
				.replaceAll("[\\x{FE0F}]", "");                   // Variation Selector-16
	}

	/**
	 * Formats a Discord timestamp (Unix epoch seconds) according to the specified style.
	 * <p>
	 * Discord timestamp styles:
	 * <ul>
	 *   <li>{@code t} - Short time (e.g., "4:20 PM")</li>
	 *   <li>{@code T} - Long time (e.g., "4:20:30 PM")</li>
	 *   <li>{@code d} - Short date (e.g., "03/12/2026")</li>
	 *   <li>{@code D} - Long date (e.g., "March 12, 2026")</li>
	 *   <li>{@code f} - Short date/time (e.g., "March 12, 2026 4:20 PM") - default</li>
	 *   <li>{@code F} - Long date/time (e.g., "Thursday, March 12, 2026 4:20 PM")</li>
	 *   <li>{@code R} - Relative time (e.g., "2 hours ago")</li>
	 * </ul>
	 *
	 * @param epochSeconds The Unix timestamp in seconds
	 * @param style        The style character (t/T/d/D/f/F/R), or null for default (f)
	 * @return The formatted timestamp string
	 */
	private static String formatDiscordTimestamp(long epochSeconds, String style) {
		java.time.Instant instant = java.time.Instant.ofEpochSecond(epochSeconds);
		java.time.ZonedDateTime dateTime = instant.atZone(java.time.ZoneId.systemDefault());

		// Determine locale from DMCC language setting
		java.util.Locale locale = resolveLocale();

		if (style == null) style = "f"; // default to short date/time

		return switch (style) {
			case "t" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale));
			case "T" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.MEDIUM).withLocale(locale));
			case "d" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale));
			case "D" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale));
			case "f" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.LONG, java.time.format.FormatStyle.SHORT).withLocale(locale));
			case "F" -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.FULL, java.time.format.FormatStyle.SHORT).withLocale(locale));
			case "R" -> formatRelativeTime(instant);
			default -> dateTime.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.LONG, java.time.format.FormatStyle.SHORT).withLocale(locale));
		};
	}

	/**
	 * Resolves the Java Locale from the DMCC language setting.
	 *
	 * @return The resolved Locale
	 */
	private static java.util.Locale resolveLocale() {
		String lang = I18nManager.getLanguage();
		if (lang != null && lang.contains("_")) {
			String[] parts = lang.split("_", 2);
			return java.util.Locale.of(parts[0], parts[1].toUpperCase());
		}
		return java.util.Locale.US;
	}

	/**
	 * Formats a relative time string (e.g., "2 hours ago", "in 3 days").
	 * Uses DMCC translations for localization.
	 *
	 * @param instant The point in time
	 * @return The formatted relative time string
	 */
	private static String formatRelativeTime(java.time.Instant instant) {
		java.time.Instant now = java.time.Instant.now();
		long totalSeconds = java.time.temporal.ChronoUnit.SECONDS.between(instant, now);
		boolean past = totalSeconds >= 0;
		totalSeconds = Math.abs(totalSeconds);

		long value;
		String unit;
		if (totalSeconds < 60) {
			value = totalSeconds;
			unit = "seconds";
		} else if (totalSeconds < 3600) {
			value = totalSeconds / 60;
			unit = "minutes";
		} else if (totalSeconds < 86400) {
			value = totalSeconds / 3600;
			unit = "hours";
		} else if (totalSeconds < 2592000) { // ~30 days
			value = totalSeconds / 86400;
			unit = "days";
		} else if (totalSeconds < 31536000) { // ~365 days
			value = totalSeconds / 2592000;
			unit = "months";
		} else {
			value = totalSeconds / 31536000;
			unit = "years";
		}

		// Use i18n for relative time formatting
		if (past) {
			return I18nManager.getDmccTranslation("discord.timestamp.relative.past." + unit, String.valueOf(value));
		} else {
			return I18nManager.getDmccTranslation("discord.timestamp.relative.future." + unit, String.valueOf(value));
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
