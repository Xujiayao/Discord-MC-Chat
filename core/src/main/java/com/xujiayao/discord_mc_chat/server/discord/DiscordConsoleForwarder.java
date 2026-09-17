package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.utils.CachedPatterns;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Mirrors DMCC client consoles into Discord channels, in both directions.
 * <p>
 * Outgoing lines are redacted through {@code console_forwarding.filter_regex}, wrapped in inline code so
 * Discord does not render them as markdown, batched up to a size Discord accepts, and posted under the
 * source client's identity. The reverse direction, deciding which client a message in a forwarding channel
 * should be sent to, is {@link #resolveTargetServer}.
 *
 * @author Xujiayao
 */
public final class DiscordConsoleForwarder {

	private static final int CHUNK_LIMIT = 1800;
	private static final int INLINE_LIMIT = 1200;

	/**
	 * Clients whose forwarding channel could not be resolved. Retrying the lookup for every incoming batch
	 * would only repeat the same error, so a client is skipped until forwarding starts again.
	 */
	private static final Set<String> DISABLED_CLIENTS = ConcurrentHashMap.newKeySet();

	// Compiled console redaction patterns, rebuilt only when console_forwarding.filter_regex changes.
	private static final CachedPatterns REDACTION_PATTERNS = new CachedPatterns();

	private DiscordConsoleForwarder() {
	}

	// ===== Outgoing: console to Discord =====

	/**
	 * Sends one batch of console output to the channel configured for that client.
	 *
	 * @param clientName DMCC client/server name.
	 * @param lines      Console log lines to forward.
	 */
	public static void sendBatch(String clientName, List<String> lines) {
		if (!ConfigManager.getBoolean("console_forwarding.enable") || lines == null || lines.isEmpty()) {
			return;
		}
		if (DISABLED_CLIENTS.contains(clientName)) {
			return;
		}

		String channelIdentifier = DiscordSender.resolveConsoleChannel(clientName);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}

		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) {
			DISABLED_CLIENTS.add(clientName);
			return;
		}

		StringBuilder batch = new StringBuilder();
		for (String rawLine : lines) {
			for (String line : formatLine(rawLine)) {
				if (!batch.isEmpty() && batch.length() + line.length() + 1 > CHUNK_LIMIT) {
					sendChunk(channel, channelIdentifier, clientName, batch.toString());
					batch.setLength(0);
				}

				if (!batch.isEmpty()) {
					batch.append("\n");
				}
				batch.append(line);
			}
		}

		if (!batch.isEmpty()) {
			sendChunk(channel, channelIdentifier, clientName, batch.toString());
		}
	}

	/**
	 * Announces that forwarding for one client started or stopped.
	 *
	 * @param started true when forwarding starts; false when it stops.
	 */
	public static void sendStatus(String clientName, boolean started) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return;
		}
		if (started) {
			DISABLED_CLIENTS.remove(clientName);
		}
		JDA jda = DiscordManager.getJda();
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return;
		}

		String channelIdentifier = DiscordSender.resolveConsoleChannel(clientName);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}

		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) {
			DISABLED_CLIENTS.add(clientName);
			return;
		}

		String message = statusMessage(started ? "started" : "stopped", clientName);
		if (message.isBlank()) {
			return;
		}

		try {
			if ("standalone".equals(ConfigManager.getMode())) {
				DiscordSender.sendSync(channel, clientName, DiscordSender.clientAvatarUrl(clientName), message, true);
			} else {
				DiscordManager.sendBotMessageSync(channelIdentifier, message);
			}
		} catch (RejectedExecutionException ignored) {
			// JDA may reject tasks during shutdown races; ignore to avoid noisy stack traces.
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	// ===== Incoming: Discord to console =====

	/**
	 * @param channelId   Discord channel ID.
	 * @param channelName Discord channel name.
	 * @return The client whose console the channel feeds, or null when the channel is not configured.
	 */
	public static String resolveTargetServer(String channelId, String channelName) {
		return DiscordSender.resolveConsoleTargetServer(channelId, channelName);
	}

	// ===== Formatting =====

	private static void sendChunk(TextChannel channel, String channelIdentifier, String clientName, String chunk) {
		if (chunk == null || chunk.isBlank()) {
			return;
		}
		try {
			if ("standalone".equals(ConfigManager.getMode())) {
				DiscordSender.send(channel, clientName, DiscordSender.clientAvatarUrl(clientName), chunk, true);
			} else {
				DiscordManager.sendBotMessage(channelIdentifier, null, chunk);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	private static String statusMessage(String key, String clientName) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		JsonNode statusNode = customMessages.path("console_forwarding").path(key);
		if (statusNode.isMissingNode() || statusNode.isNull()) {
			return "";
		}

		String mode = "standalone".equals(ConfigManager.getMode()) ? "standalone" : "single_server";
		String template = statusNode.path(mode).asString(statusNode.asString(""));
		if (template.isBlank()) {
			return "";
		}

		return template.replace("{server}", clientName == null ? "" : clientName);
	}

	/**
	 * @param rawLine One raw console line.
	 * @return The line redacted, stripped of line breaks and backticks, and split into inline-code parts.
	 */
	private static List<String> formatLine(String rawLine) {
		String line = applyRedaction(rawLine == null ? "" : rawLine)
				.replace("\r", " ")
				.replace("\n", " ")
				.replace("`", "'");
		if (line.isBlank()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		int index = 0;
		while (index < line.length()) {
			int end = Math.min(index + INLINE_LIMIT, line.length());
			result.add("`" + line.substring(index, end) + "`");
			index = end;
		}
		return result;
	}

	/**
	 * Applies the configured console redaction patterns.
	 * <p>
	 * The patterns are compiled once per configuration revision rather than per log line, because this runs
	 * on the console forwarding hot path.
	 */
	private static String applyRedaction(String message) {
		String output = message;
		for (Pattern pattern : redactionPatterns()) {
			output = pattern.matcher(output).replaceAll("redacted");
		}
		return output;
	}

	/**
	 * @return The compiled {@code console_forwarding.filter_regex} patterns, recompiled only when the
	 * configured list actually changes.
	 */
	private static List<Pattern> redactionPatterns() {
		return REDACTION_PATTERNS.of("console_forwarding.filter_regex",
				regex -> LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.invalid_console_filter_regex", regex)));
	}
}
