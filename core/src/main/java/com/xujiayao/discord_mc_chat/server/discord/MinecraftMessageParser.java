package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Minecraft-originated text into Discord-ready content.
 * <p>
 * All parsing decisions are driven by {@code message_parsing.minecraft_to_discord.*}
 * in single_server mode, or {@code message_parsing.minecraft_to_xxxxx.*} in standalone mode.
 *
 * @author Xujiayao
 */
public class MinecraftMessageParser {

	private static final Pattern MARKDOWN_ESCAPE_PATTERN = Pattern.compile("([\\\\`*_{}\\[\\]()#+\\-.!|>~])");
	private static final Pattern UNICODE_EMOJI_PATTERN = Pattern.compile(
			"[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
					"[\\x{1F1E0}-\\x{1F1FF}]|[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}]|" +
					"[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA70}-\\x{1FAFF}]|[\\x{1F018}-\\x{1F270}]|" +
					"[\\x{238C}-\\x{2454}]|[\\x{20D0}-\\x{20FF}]"
	);
	private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:\\w+:(\\d+)>");
	private static final Pattern MINECRAFT_MENTION_PATTERN = Pattern.compile("@([\\w.-]{1,32})");
	private static final Pattern LINK_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>()\\[\\]{}\"']+");
	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:\\d+(?::[tTdDfFR])?>");
	private static final Pattern MINECRAFT_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

	private MinecraftMessageParser() {
	}

	/**
	 * Parses message text using configured minecraft_to_xxxxx switches.
	 *
	 * @param message The original message text.
	 * @return Parsed message text for Discord output.
	 */
	public static String parseMessage(String message) {
		if (message == null || message.isEmpty()) {
			return "";
		}

		boolean parseMarkdown = getParseFlag("markdown");
		boolean parseUnicodeEmojis = getParseFlag("unicode_emojis");
		boolean parseCustomEmojis = getParseFlag("custom_emojis");
		boolean parseMentions = getParseFlag("mentions");
		boolean parseHyperlinks = getParseFlag("hyperlinks");
		boolean parseTimestamps = getParseFlag("timestamps");

		String parsed = stripMinecraftFormattingCodes(message);

		if (!parseUnicodeEmojis) {
			parsed = removeUnicodeEmojis(parsed);
		}
		if (!parseCustomEmojis) {
			parsed = stripCustomEmojis(parsed);
		}
		if (!parseMentions) {
			parsed = neutralizeMentions(parsed);
		}
		if (!parseHyperlinks) {
			parsed = neutralizeLinks(parsed);
		}
		if (!parseTimestamps) {
			parsed = neutralizeDiscordTimestamps(parsed);
		}
		if (!parseMarkdown) {
			parsed = escapeDiscordMarkdown(parsed);
		}

		return parsed;
	}

	private static boolean getParseFlag(String key) {
		String mode = ModeManager.getMode();
		String root = "single_server".equals(mode)
				? "message_parsing.minecraft_to_discord."
				: "message_parsing.minecraft_to_xxxxx.";
		Boolean value = ConfigManager.getBoolean(root + key);
		return value != null && value;
	}

	private static String stripMinecraftFormattingCodes(String text) {
		return MINECRAFT_FORMATTING_PATTERN.matcher(text).replaceAll("");
	}

	private static String removeUnicodeEmojis(String text) {
		return UNICODE_EMOJI_PATTERN.matcher(text).replaceAll("");
	}

	private static String stripCustomEmojis(String text) {
		return CUSTOM_EMOJI_PATTERN.matcher(text).replaceAll(":emoji:");
	}

	private static String neutralizeMentions(String text) {
		Matcher matcher = MINECRAFT_MENTION_PATTERN.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, "@\u200B" + Matcher.quoteReplacement(matcher.group(1)));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String neutralizeLinks(String text) {
		Matcher matcher = LINK_PATTERN.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String replacement = "<" + matcher.group() + ">";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String neutralizeDiscordTimestamps(String text) {
		Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String replacement = "`" + matcher.group() + "`";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String escapeDiscordMarkdown(String text) {
		return MARKDOWN_ESCAPE_PATTERN.matcher(text).replaceAll("\\\\$1");
	}
}
