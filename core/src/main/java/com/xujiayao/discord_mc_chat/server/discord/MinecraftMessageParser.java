package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.fellbaum.jemoji.EmojiManager;

import java.util.regex.Pattern;

/**
 * Parses Minecraft-originated text before it is sent to Discord.
 * <p>
 * Parsing behavior is controlled by {@code message_parsing.minecraft_to_discord.*}
 * switches in configuration files.
 *
 * @author Xujiayao
 */
public class MinecraftMessageParser {

	private static final Pattern DISCORD_CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:[A-Za-z0-9_~]+:\\d+>");
	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:-?\\d{1,17}(?::[tTdDfFR])?>");
	private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
	private static final Pattern EVERYONE_HERE_PATTERN = Pattern.compile("@(everyone|here)");
	private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");
	// Matches Discord Markdown/meta characters. `\\\\` in Java string is a literal backslash in regex.
	private static final Pattern MARKDOWN_SPECIAL_PATTERN = Pattern.compile("([\\\\`*_{}\\[\\]()#+\\-.!|>~])");

	private MinecraftMessageParser() {
	}

	/**
	 * Parses a Minecraft text payload for safe/expected Discord rendering.
	 *
	 * @param raw The raw Minecraft text.
	 * @return The parsed text ready for Discord.
	 */
	public static String parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}

		String parsed = raw;

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.timestamps")) {
			parsed = DISCORD_TIMESTAMP_PATTERN.matcher(parsed).replaceAll(m -> "\\\\" + m.group());
		}

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.custom_emojis")) {
			parsed = DISCORD_CUSTOM_EMOJI_PATTERN.matcher(parsed).replaceAll(m -> "\\\\" + m.group());
		}

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.unicode_emojis")) {
			parsed = EmojiManager.replaceAllEmojis(parsed, emoji -> "\\\\" + emoji.getEmoji());
		}

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.mentions")) {
			parsed = EVERYONE_HERE_PATTERN.matcher(parsed).replaceAll("@\u200B$1");
			parsed = USER_MENTION_PATTERN.matcher(parsed).replaceAll("<@\u200B$1>");
			parsed = ROLE_MENTION_PATTERN.matcher(parsed).replaceAll("<@&\u200B$1>");
			parsed = CHANNEL_MENTION_PATTERN.matcher(parsed).replaceAll("<#\u200B$1>");
		}

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.hyperlinks")) {
			parsed = URL_PATTERN.matcher(parsed).replaceAll(m -> m.group().replace("://", ":\u200B//"));
		}

		if (!ConfigManager.getBoolean("message_parsing.minecraft_to_discord.markdown")) {
			parsed = MARKDOWN_SPECIAL_PATTERN.matcher(parsed)
					.replaceAll(match -> "\\\\" + match.group(1));
		}

		return parsed;
	}
}
