package com.xujiayao.discord_mc_chat.server.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.fellbaum.jemoji.EmojiManager;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds pre-rendered TextSegments for Minecraft-origin chat and events.
 * <p>
 * The server is responsible for formatting and parsing. DMCC clients only render received segments.
 *
 * @author Xujiayao
 */
public class MinecraftMessageParser {

	private static final Pattern USER_MENTION_PATTERN = Pattern.compile("(?<!\\w)@([A-Za-z0-9_]{3,16})");
	private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");
	private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
	private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
	private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__([^_]+)__");
	private static final Pattern STRIKE_PATTERN = Pattern.compile("~~([^~]+)~~");
	private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
	private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("(?<![A-Za-z0-9_]):[A-Za-z0-9_+\\-]+:(?![A-Za-z0-9_])");

	private static final String URL_COLOR = "#3366CC";

	private MinecraftMessageParser() {
	}

	/**
	 * Returns true when at least one minecraft-origin parsing toggle is enabled.
	 *
	 * @return whether any parsing toggle is enabled.
	 */
	public static boolean isAnyParsingEnabled() {
		String base = getParsingConfigPath();
		return ConfigManager.getBoolean(base + ".markdown")
				|| ConfigManager.getBoolean(base + ".unicode_emojis")
				|| ConfigManager.getBoolean(base + ".custom_emojis")
				|| ConfigManager.getBoolean(base + ".mentions")
				|| ConfigManager.getBoolean(base + ".hyperlinks")
				|| ConfigManager.getBoolean(base + ".timestamps");
	}

	/**
	 * Resolves sender role color for Minecraft-origin routed chat.
	 *
	 * @param playerUuid Minecraft player UUID string.
	 * @return role color hex or white.
	 */
	public static String resolveSenderRoleColor(String playerUuid) {
		boolean useRoleColor = Boolean.TRUE.equals(ConfigManager.getBoolean("account_linking.use_linked_discord_role_color_for_minecraft_messages"));
		if (!useRoleColor) {
			return "white";
		}
		String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(playerUuid);
		if (discordId == null || discordId.isBlank()) {
			return "white";
		}

		Member member = DiscordManager.retrieveMember(discordId);
		if (member == null) {
			return "white";
		}

		Color color = member.getColors().getPrimary();
		if (color == null) {
			return "white";
		}
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/**
	 * Builds a chat line from {@code common.message}.
	 *
	 * @param serverName  source server name.
	 * @param serverColor source server color.
	 * @param displayName sender display name.
	 * @param roleColor   sender role color.
	 * @param rawMessage  raw message body.
	 * @return final segments.
	 */
	public static List<TextSegment> buildCommonMessageSegments(String serverName, String serverColor, String displayName, String roleColor, String rawMessage) {
		List<TextSegment> segments = new ArrayList<>();
		List<TextSegment> contentSegments = parse(rawMessage == null ? "" : rawMessage);

		JsonNode template = I18nManager.getCustomMessages().path("common").path("message");
		if (!template.isArray()) {
			return segments;
		}

		for (JsonNode node : template) {
			String text = node.path("text").asText("");
			boolean bold = node.path("bold").asBoolean(false);
			String color = node.path("color").asText("");

			text = replaceMessagePlaceholders(text, serverName, serverColor, displayName, roleColor);
			color = replaceMessagePlaceholders(color, serverName, serverColor, displayName, roleColor);

			if (!text.contains("{message}")) {
				segments.add(new TextSegment(text, bold, color));
				continue;
			}

			String[] parts = text.split("\\{message}", -1);
			if (!parts[0].isEmpty()) {
				segments.add(new TextSegment(parts[0], bold, color));
			}

			for (TextSegment content : contentSegments) {
				TextSegment copy = copySegment(content);
				if (copy.color == null || copy.color.isEmpty()) {
					copy.color = color;
				}
				segments.add(copy);
			}

			if (parts.length > 1 && !parts[1].isEmpty()) {
				segments.add(new TextSegment(parts[1], bold, color));
			}
		}

		return segments;
	}

	/**
	 * Builds an event line from {@code common.others}.
	 *
	 * @param serverName  source server name.
	 * @param serverColor source server color.
	 * @param message     event message.
	 * @return final segments.
	 */
	public static List<TextSegment> buildCommonOthersSegments(String serverName, String serverColor, String message) {
		List<TextSegment> segments = new ArrayList<>();
		JsonNode template = I18nManager.getCustomMessages().path("common").path("others");
		if (!template.isArray()) {
			return segments;
		}

		for (JsonNode node : template) {
			String text = node.path("text").asText("");
			boolean bold = node.path("bold").asBoolean(false);
			String color = node.path("color").asText("");

			text = text.replace("{server}", serverName)
					.replace("{server_color}", serverColor)
					.replace("{message}", message);
			color = color.replace("{server_color}", serverColor);

			segments.add(new TextSegment(text, bold, color));
		}
		return segments;
	}

	private static List<TextSegment> parse(String raw) {
		List<TextSegment> segments = new ArrayList<>();
		segments.add(new TextSegment(raw));

		String base = getParsingConfigPath();
		if (ConfigManager.getBoolean(base + ".mentions")) {
			segments = parseMentions(segments);
		}
		if (ConfigManager.getBoolean(base + ".timestamps")) {
			segments = parseTimestamps(segments);
		}
		if (ConfigManager.getBoolean(base + ".markdown")) {
			segments = parseMarkdown(segments);
		}
		if (ConfigManager.getBoolean(base + ".hyperlinks")) {
			segments = parseHyperlinks(segments);
		}
		if (ConfigManager.getBoolean(base + ".custom_emojis")) {
			segments = parseCustomEmojis(segments);
		}
		if (ConfigManager.getBoolean(base + ".unicode_emojis")) {
			segments = parseUnicodeEmojis(segments);
		}
		return segments;
	}

	private static List<TextSegment> parseMentions(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}
			Matcher matcher = USER_MENTION_PATTERN.matcher(seg.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					result.add(copySegmentWithText(seg, seg.text.substring(cursor, matcher.start())));
				}
				TextSegment mention = copySegmentWithText(seg, "[@" + matcher.group(1) + "]");
				mention.color = "yellow";
				result.add(mention);
				cursor = matcher.end();
			}
			if (cursor < seg.text.length()) {
				result.add(copySegmentWithText(seg, seg.text.substring(cursor)));
			}
		}
		return result;
	}

	private static List<TextSegment> parseTimestamps(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}

			Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(seg.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					result.add(copySegmentWithText(seg, seg.text.substring(cursor, matcher.start())));
				}
				TextSegment ts = copySegmentWithText(seg, matcher.group());
				try {
					long epoch = Long.parseLong(matcher.group(1));
					ts.text = "[" + formatDiscordTimestamp(epoch, matcher.group(2)) + "]";
					ts.color = "yellow";
				} catch (Exception ignored) {
				}
				result.add(ts);
				cursor = matcher.end();
			}
			if (cursor < seg.text.length()) {
				result.add(copySegmentWithText(seg, seg.text.substring(cursor)));
			}
		}
		return result;
	}

	private static List<TextSegment> parseMarkdown(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}
			String text = seg.text;
			text = BOLD_PATTERN.matcher(text).replaceAll("$1");
			text = ITALIC_PATTERN.matcher(text).replaceAll("$1");
			text = UNDERLINE_PATTERN.matcher(text).replaceAll("$1");
			text = STRIKE_PATTERN.matcher(text).replaceAll("$1");
			text = SPOILER_PATTERN.matcher(text).replaceAll("$1");
			result.add(copySegmentWithText(seg, text));
		}
		return result;
	}

	private static List<TextSegment> parseHyperlinks(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}
			Matcher matcher = BARE_URL_PATTERN.matcher(seg.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					result.add(copySegmentWithText(seg, seg.text.substring(cursor, matcher.start())));
				}
				String url = matcher.group(1);
				TextSegment urlSegment = copySegmentWithText(seg, url);
				urlSegment.color = URL_COLOR;
				urlSegment.underlined = true;
				urlSegment.clickUrl = url;
				urlSegment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				result.add(urlSegment);
				cursor = matcher.end();
			}
			if (cursor < seg.text.length()) {
				result.add(copySegmentWithText(seg, seg.text.substring(cursor)));
			}
		}
		return result;
	}

	private static List<TextSegment> parseCustomEmojis(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}
			Matcher matcher = CUSTOM_EMOJI_PATTERN.matcher(seg.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					result.add(copySegmentWithText(seg, seg.text.substring(cursor, matcher.start())));
				}
				TextSegment emojiSegment = copySegmentWithText(seg, matcher.group());
				emojiSegment.color = "yellow";
				result.add(emojiSegment);
				cursor = matcher.end();
			}
			if (cursor < seg.text.length()) {
				result.add(copySegmentWithText(seg, seg.text.substring(cursor)));
			}
		}
		return result;
	}

	private static List<TextSegment> parseUnicodeEmojis(List<TextSegment> source) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment seg : source) {
			if (!isSimpleSegment(seg)) {
				result.add(seg);
				continue;
			}
			String replaced = seg.text;
			for (var emoji : EmojiManager.extractEmojisInOrder(seg.text)) {
				if (!emoji.getDiscordAliases().isEmpty()) {
					replaced = replaced.replace(emoji.getEmoji(), emoji.getDiscordAliases().getFirst());
				}
			}
			result.add(copySegmentWithText(seg, replaced));
		}
		return result;
	}

	private static String getParsingConfigPath() {
		return "single_server".equals(ModeManager.getMode())
				? "message_parsing.minecraft_to_discord"
				: "message_parsing.minecraft_to_xxxxx";
	}

	private static boolean isSimpleSegment(TextSegment seg) {
		return seg != null
				&& seg.text != null
				&& (seg.clickUrl == null || seg.clickUrl.isEmpty())
				&& (seg.hoverText == null || seg.hoverText.isEmpty());
	}

	private static String replaceMessagePlaceholders(String value, String serverName, String serverColor, String displayName, String roleColor) {
		return value.replace("{server}", serverName)
				.replace("{server_color}", serverColor)
				.replace("{effective_name}", displayName)
				.replace("{role_color}", roleColor);
	}

	private static TextSegment copySegment(TextSegment source) {
		return copySegmentWithText(source, source.text);
	}

	private static TextSegment copySegmentWithText(TextSegment source, String text) {
		TextSegment copy = new TextSegment(text, source.bold, source.color);
		copy.italic = source.italic;
		copy.underlined = source.underlined;
		copy.strikethrough = source.strikethrough;
		copy.obfuscated = source.obfuscated;
		copy.clickUrl = source.clickUrl;
		copy.hoverText = source.hoverText;
		return copy;
	}

	private static String formatDiscordTimestamp(long epoch, String style) {
		Instant instant = Instant.ofEpochSecond(epoch);
		Locale locale = toLocale(I18nManager.getLanguage());
		ZoneId zone = ZoneId.systemDefault();
		String timestampStyle = style == null ? "f" : style;

		return switch (timestampStyle) {
			case "t" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "T" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone));
			case "d" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "D" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(instant.atZone(zone));
			case "F" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "R" -> formatRelative(Instant.now().getEpochSecond() - epoch);
			case "s", "S" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			default -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
		};
	}

	private static String formatRelative(long diffSeconds) {
		boolean past = diffSeconds >= 0;
		long abs = Math.abs(diffSeconds);

		String unitKey;
		long value;
		if (abs < 60) {
			unitKey = "second";
			value = abs;
		} else if (abs < 3600) {
			unitKey = "minute";
			value = abs / 60;
		} else if (abs < 86400) {
			unitKey = "hour";
			value = abs / 3600;
		} else if (abs < 2592000) {
			unitKey = "day";
			value = abs / 86400;
		} else if (abs < 31536000) {
			unitKey = "month";
			value = abs / 2592000;
		} else {
			unitKey = "year";
			value = abs / 31536000;
		}

		String unit = I18nManager.getDmccTranslation(
				"discord.message_parser.relative.units." + unitKey + "." + (value == 1 ? "one" : "other")
		);
		return past
				? I18nManager.getDmccTranslation("discord.message_parser.relative.past", value, unit)
				: I18nManager.getDmccTranslation("discord.message_parser.relative.future", value, unit);
	}

	private static Locale toLocale(String languageCode) {
		if (languageCode == null || languageCode.isBlank()) {
			return Locale.ENGLISH;
		}
		String tag = languageCode.replace('_', '-');
		Locale locale = Locale.forLanguageTag(tag);
		return locale.getLanguage().isBlank() ? Locale.ENGLISH : locale;
	}
}
