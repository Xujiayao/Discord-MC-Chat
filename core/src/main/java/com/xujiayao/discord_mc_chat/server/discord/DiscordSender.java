package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Resolves the Discord channels DMCC talks to, and delivers messages into them.
 * <p>
 * A "channel identifier" in DMCC config is either a channel ID or a channel name, so every lookup goes
 * through {@link #find(String)} and reports the same diagnostic when it cannot resolve one.
 * <p>
 * Delivery has two identities. Ordinary messages are posted by the bot. Messages that have to appear as a
 * DMCC client or a Discord user — the standalone console relay, the {@code /console} and {@code /execute}
 * results, Minecraft chat with fake user style — go through the channel's {@code "DMCC Webhook"} instead;
 * that handle is cached per channel because resolving it is a blocking REST call.
 *
 * @author Xujiayao
 */
public final class DiscordSender {

	/**
	 * Matches a {@code :alias:} emoji token, whose underscores must not be read as Markdown emphasis.
	 */
	private static final Pattern EMOJI_ALIAS_PATTERN = Pattern.compile("(:[^:]+:)");

	/**
	 * Resolved "DMCC Webhook" handle per channel ID. Stale handles are dropped when a send reports that the
	 * webhook is gone, so the next send resolves a fresh one.
	 */
	private static final Map<String, Webhook> WEBHOOK_CACHE = new ConcurrentHashMap<>();

	private DiscordSender() {
	}

	// ===== Resolution =====

	/**
	 * @param identifier Channel ID or channel name, or blank to disable the target.
	 * @return The resolved channel, or null when it cannot be used (with a logged reason).
	 */
	static TextChannel find(String identifier) {
		JDA jda = DiscordManager.getJda();
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return null;
		}

		if (identifier == null || identifier.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
			return null;
		}

		TextChannel channel;
		String normalizedIdentifier = identifier.trim();

		// Try search by name. Return the first result; use with caution if several channels share a name.
		List<TextChannel> channels = jda.getTextChannelsByName(normalizedIdentifier, true);
		if (!channels.isEmpty()) {
			channel = channels.getFirst();
		} else {
			// Try parsing as ID only when the identifier is a valid snowflake.
			boolean numericId = !normalizedIdentifier.isEmpty();
			for (int i = 0; i < normalizedIdentifier.length(); i++) {
				if (!Character.isDigit(normalizedIdentifier.charAt(i))) {
					numericId = false;
					break;
				}
			}

			if (!numericId) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
				return null;
			}

			channel = jda.getTextChannelById(normalizedIdentifier);
			if (channel == null) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
				return null;
			}
		}

		if (!channel.canTalk()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_cannot_talk", identifier));
			return null;
		}

		return channel;
	}

	/**
	 * @param channelId   Discord channel ID.
	 * @param channelName Discord channel name.
	 * @return The client whose console the channel forwards, or null when the channel is not configured.
	 */
	public static String resolveConsoleTargetServer(String channelId, String channelName) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return null;
		}

		if ("standalone".equals(ConfigManager.getMode())) {
			JsonNode channels = ConfigManager.getConfigNode("console_forwarding.channels");
			if (!channels.isArray()) {
				return null;
			}
			for (int i = 0; i < channels.size(); i++) {
				JsonNode node = channels.get(i);
				String server = node.path("server").asString("").trim();
				String configuredChannel = node.path("channel").asString("").trim();
				String configPath = "console_forwarding.channels[" + i + "]";
				if (server.isBlank()) {
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", configPath + ".server"));
					continue;
				}
				if (configuredChannel.isBlank()) {
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", configPath + ".channel"));
					continue;
				}
				if (matches(configuredChannel, channelId, channelName)) {
					return server;
				}
			}
			return null;
		}

		String configured = ConfigManager.getString("console_forwarding.channel", "");
		if (configured == null || configured.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", "console_forwarding.channel"));
			return null;
		}
		return matches(configured, channelId, channelName) ? "Internal" : null;
	}

	/**
	 * @param clientName DMCC client/server name.
	 * @return The console channel configured for that client, or an empty string.
	 */
	public static String resolveConsoleChannel(String clientName) {
		if ("standalone".equals(ConfigManager.getMode())) {
			JsonNode channels = ConfigManager.getConfigNode("console_forwarding.channels");
			if (!channels.isArray()) {
				return "";
			}
			for (JsonNode node : channels) {
				if (clientName.equals(node.path("server").asString(""))) {
					return node.path("channel").asString("");
				}
			}
			return "";
		}

		return ConfigManager.getString("console_forwarding.channel", "");
	}

	private static boolean matches(String configuredChannel, String channelId, String channelName) {
		if (configuredChannel == null || configuredChannel.isBlank()) {
			return false;
		}
		return configuredChannel.equals(channelId) || configuredChannel.equalsIgnoreCase(channelName);
	}

	// ===== Delivery as the bot =====

	/**
	 * Reads the mentions DMCC is allowed to resolve, from {@code discord.allow_mentions}.
	 */
	static List<Message.MentionType> allowedMentions() {
		List<Message.MentionType> allowedMentions = new ArrayList<>();
		JsonNode allowMentionsNode = ConfigManager.getConfigNode("discord.allow_mentions");
		if (allowMentionsNode.isArray()) {
			for (JsonNode node : allowMentionsNode) {
				switch (node.asString()) {
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
	 * Posts one already-formatted message to a channel and mirrors it into the DMCC log.
	 * <p>
	 * In standalone mode the message is sent through the source client's webhook identity and every log line
	 * is prefixed with that client name; otherwise the bot posts it directly.
	 *
	 * @param channel           Resolved target channel.
	 * @param channelIdentifier Configured channel identifier, used when the bot posts directly.
	 * @param clientName        DMCC client/server name used as the webhook identity in standalone mode.
	 * @param message           Message content to post.
	 * @param logMessage        Plain-text variant used for the log lines.
	 */
	static void postServerMessage(TextChannel channel, String channelIdentifier, String clientName,
								  String message, String logMessage) {
		boolean standaloneMode = "standalone".equals(ConfigManager.getMode());
		if (standaloneMode) {
			send(channel, clientName, clientAvatarUrl(clientName), message, true);
		} else {
			DiscordManager.sendBotMessage(channelIdentifier, null, message);
		}
		for (String line : logMessage.split("\\n")) {
			String sanitized = sanitizeLineForLogging(line);
			if (standaloneMode) {
				LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitized);
			} else {
				LOGGER.info(sanitized);
			}
		}
	}

	/**
	 * @param clientName DMCC client/server name.
	 * @return The avatar configured for that client, or the bot's own avatar.
	 */
	static String clientAvatarUrl(String clientName) {
		String avatarUrl = "";
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode != null && serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (clientName.equals(node.path("name").asString())) {
					avatarUrl = node.path("avatar_url").asString();
				}
			}
		}
		if (avatarUrl == null || avatarUrl.isBlank()) {
			avatarUrl = DiscordManager.getJda().getSelfUser().getEffectiveAvatarUrl();
		}
		return avatarUrl;
	}

	/**
	 * @param line One console log line.
	 * @return The line with Discord markdown neutralised, ready to be written to the DMCC log.
	 */
	static String sanitizeLineForLogging(String line) {
		// Escape underscores in :emoji: to prevent being treated as Markdown formatting
		line = EMOJI_ALIAS_PATTERN.matcher(line).replaceAll(m -> m.group().replace("_", "\\\\_"));
		return MarkdownSanitizer.sanitize(line).replace("\\_", "_");
	}

	// ===== Delivery as a webhook identity =====

	/**
	 * Sends one message without blocking the caller.
	 *
	 * @param allowRetry true when a failed send may still invalidate the cached webhook and retry once.
	 */
	public static void send(TextChannel channel, String username, String avatarUrl, String content,
							boolean allowRetry) {
		Webhook webhook = getOrCreateWebhook(channel);
		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(allowedMentions())
				.queue(null, error -> {
					// The cached handle may have been deleted on Discord's side: drop it and retry exactly once
					// with a freshly resolved webhook instead of losing the message.
					if (allowRetry && isUnknownWebhookError(error)) {
						invalidateWebhook(channel.getId(), webhook);
						send(channel, username, avatarUrl, content, false);
						return;
					}
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", error.getLocalizedMessage()), error);
				});
	}

	/**
	 * Sends one message, blocking until the request completes.
	 *
	 * @param allowRetry true when a failed send may still invalidate the cached webhook and retry once.
	 */
	public static void sendSync(TextChannel channel, String username, String avatarUrl, String content,
								boolean allowRetry) {
		Webhook webhook = getOrCreateWebhook(channel);

		try {
			webhook.sendMessage(content)
					.setUsername(username)
					.setAvatarUrl(avatarUrl)
					.setAllowedMentions(allowedMentions())
					.complete();
		} catch (RuntimeException e) {
			if (allowRetry && isUnknownWebhookError(e)) {
				invalidateWebhook(channel.getId(), webhook);
				sendSync(channel, username, avatarUrl, content, false);
				return;
			}
			throw e;
		}
	}

	/**
	 * Sends one message with a file attachment, without blocking the caller.
	 *
	 * @param allowRetry true when a failed send may still invalidate the cached webhook and retry once.
	 */
	public static void sendWithFile(TextChannel channel, String username, String avatarUrl, String content,
									byte[] fileData, String fileName, boolean allowRetry) {
		Webhook webhook = getOrCreateWebhook(channel);
		List<Message.MentionType> allowedMentions = allowedMentions();

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(allowedMentions)
				.addFiles(FileUpload.fromData(fileData, fileName))
				.queue(null, error -> {
					if (allowRetry && isUnknownWebhookError(error)) {
						invalidateWebhook(channel.getId(), webhook);
						sendWithFile(channel, username, avatarUrl, content, fileData, fileName, false);
						return;
					}
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", error.getLocalizedMessage()), error);
				});
	}

	/**
	 * Drops every cached handle. Called when the JDA instance they belong to is going away.
	 */
	static void clearWebhookCache() {
		WEBHOOK_CACHE.clear();
	}

	/**
	 * Resolves the {@code "DMCC Webhook"} of a channel, creating it when the channel has none yet.
	 */
	private static Webhook getOrCreateWebhook(TextChannel channel) {
		Webhook cached = WEBHOOK_CACHE.get(channel.getId());
		if (cached != null) {
			return cached;
		}

		Webhook resolved = channel.retrieveWebhooks().complete()
				.stream()
				.filter(i -> "DMCC Webhook".equals(i.getName()))
				.filter(i -> i.getOwnerAsUser() == DiscordManager.getJda().getSelfUser())
				.findFirst()
				.orElseGet(() -> channel.createWebhook("DMCC Webhook").complete()); // Must use orElseGet to avoid unnecessary creation

		Webhook existing = WEBHOOK_CACHE.putIfAbsent(channel.getId(), resolved);
		return existing != null ? existing : resolved;
	}

	/**
	 * Drops a cached handle so the next send resolves a fresh one.
	 */
	private static void invalidateWebhook(String channelId, Webhook webhook) {
		// Only drop the entry when it still is the handle that just failed, so a concurrently refreshed
		// handle is never thrown away.
		WEBHOOK_CACHE.remove(channelId, webhook);
	}

	/**
	 * @param error The failure reported by JDA, possibly wrapped in additional causes.
	 * @return true when Discord answered with {@code Unknown Webhook}.
	 */
	private static boolean isUnknownWebhookError(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof ErrorResponseException responseException) {
				return responseException.getErrorResponse() == ErrorResponse.UNKNOWN_WEBHOOK;
			}
			current = current.getCause();
		}
		return false;
	}
}
