package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.message.TextSegment;
import com.xujiayao.discord_mc_chat.utils.message.TextSegmentUtils;
import net.fellbaum.jemoji.EmojiManager;

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
 * Shared parser helpers used by both Discord and Minecraft message parsing pipelines.
 *
 * @author Xujiayao
 */
final class MessageParserCommon {

	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");
	private static final Pattern LINK_TOKEN_PATTERN = Pattern.compile("\\[([^]]+)]\\((https?://[^)]+)\\)");
	private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
	private static final Pattern UNICODE_EMOJI_PATTERN = Pattern.compile(
			"[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
					"[\\x{1F1E0}-\\x{1F1FF}]|[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}]|" +
					"[\\x{FE00}-\\x{FE0F}]|[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA00}-\\x{1FA6F}]|" +
					"[\\x{1FA70}-\\x{1FAFF}]|\\x{200D}|\\x{20E3}|" +
					"[\\x{231A}-\\x{231B}]|[\\x{23E9}-\\x{23F3}]|[\\x{23F8}-\\x{23FA}]|" +
					"[\\x{25AA}-\\x{25AB}]|\\x{25B6}|\\x{25C0}|[\\x{25FB}-\\x{25FE}]|" +
					"[\\x{2614}-\\x{2615}]|[\\x{2648}-\\x{2653}]|\\x{267F}|\\x{2693}|" +
					"\\x{26A1}|[\\x{26AA}-\\x{26AB}]|[\\x{26BD}-\\x{26BE}]|" +
					"[\\x{26C4}-\\x{26C5}]|\\x{26CE}|\\x{26D4}|\\x{26EA}|" +
					"[\\x{26F2}-\\x{26F3}]|\\x{26F5}|\\x{26FA}|\\x{26FD}|" +
					"\\x{2702}|\\x{2705}|[\\x{2708}-\\x{270D}]|\\x{270F}"
	);

	private static final String URL_COLOR = "#3366CC";

	private MessageParserCommon() {
	}

	static List<TextSegment> splitSegmentsByTimestamp(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				String timestamp;
				try {
					long epoch = Long.parseLong(matcher.group(1));
					timestamp = "[" + formatDiscordTimestamp(epoch, matcher.group(2)) + "]";
				} catch (Exception ignored) {
					timestamp = matcher.group();
				}
				TextSegment ts = TextSegmentUtils.copySegment(segment, timestamp);
				ts.color = "yellow";
				out.add(ts);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	static List<TextSegment> splitSegmentsByMarkdownLink(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = LINK_TOKEN_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment link = TextSegmentUtils.copySegment(segment, matcher.group(1));
				link.clickUrl = matcher.group(2);
				link.underlined = true;
				link.color = URL_COLOR;
				link.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(link);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	static List<TextSegment> splitSegmentsByBareUrl(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = BARE_URL_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment url = TextSegmentUtils.copySegment(segment, matcher.group(1));
				url.clickUrl = matcher.group(1);
				url.underlined = true;
				url.color = URL_COLOR;
				url.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(url);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	static List<TextSegment> splitSegmentsByUnicodeEmoji(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = UNICODE_EMOJI_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				String unicodeEmoji = matcher.group();
				String alias = EmojiManager.replaceAllEmojis(unicodeEmoji, emoji -> emoji.getDiscordAliases().getFirst());
				TextSegment emojiSegment = TextSegmentUtils.copySegment(segment, alias);
				emojiSegment.color = "yellow";
				out.add(emojiSegment);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	static boolean isUnderscoreDelimiter(String delimiter) {
		return "_".equals(delimiter) || "__".equals(delimiter);
	}

	static boolean isInsideDiscordAliasEmoji(String text, int index, Pattern discordAliasEmojiPattern) {
		if (index <= 0 || index >= text.length()) {
			return false;
		}
		int leftColon = text.lastIndexOf(':', index);
		if (leftColon < 0) {
			return false;
		}
		int rightColon = text.indexOf(':', index);
		if (rightColon < 0 || rightColon <= leftColon + 1) {
			return false;
		}
		if (index <= leftColon || index >= rightColon) {
			return false;
		}
		String candidate = text.substring(leftColon, rightColon + 1);
		return discordAliasEmojiPattern.matcher(candidate).matches();
	}

	static String formatDiscordTimestamp(long epoch, String style) {
		Instant instant = Instant.ofEpochSecond(epoch);
		Locale locale = getDmccLocale();
		ZoneId zone = ZoneId.systemDefault();

		if (style == null) {
			style = "f";
		}

		return switch (style) {
			case "t" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "T" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
					.format(instant.atZone(zone));
			case "d" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "D" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
					.format(instant.atZone(zone));
			case "s" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "S" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).withLocale(locale)
					.format(instant.atZone(zone));
			case "F" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "R" -> {
				long now = Instant.now().getEpochSecond();
				long diff = now - epoch;
				yield formatRelativeTime(diff);
			}
			default -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
		};
	}

	private static String formatRelativeTime(long diffSeconds) {
		boolean past = diffSeconds >= 0;
		long abs = Math.abs(diffSeconds);

		String unitKey;
		long value;
		if (abs < 60) {
			value = abs;
			unitKey = "second";
		} else if (abs < 3600) {
			value = abs / 60;
			unitKey = "minute";
		} else if (abs < 86400) {
			value = abs / 3600;
			unitKey = "hour";
		} else if (abs < 2592000) {
			value = abs / 86400;
			unitKey = "day";
		} else if (abs < 31536000) {
			value = abs / 2592000;
			unitKey = "month";
		} else {
			value = abs / 31536000;
			unitKey = "year";
		}

		String unitTranslationKey = String.format(
				"discord.message_parser.relative.units.%s.%s",
				unitKey,
				value == 1 ? "one" : "other"
		);
		String unit = I18nManager.getDmccTranslation(unitTranslationKey);
		return past
				? I18nManager.getDmccTranslation("discord.message_parser.relative.past", value, unit)
				: I18nManager.getDmccTranslation("discord.message_parser.relative.future", value, unit);
	}

	private static Locale getDmccLocale() {
		String languageCode = I18nManager.getLanguage();
		if (languageCode == null || languageCode.isBlank()) {
			return Locale.ENGLISH;
		}
		String tag = languageCode.replace('_', '-');
		Locale locale = Locale.forLanguageTag(tag);
		if (locale.getLanguage().isBlank()) {
			return Locale.ENGLISH;
		}
		return locale;
	}
}
