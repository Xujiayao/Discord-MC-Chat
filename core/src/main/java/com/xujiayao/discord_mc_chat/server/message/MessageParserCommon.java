package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
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
 * Shared patterns and helpers used by both the Discord and the Minecraft message parsing pipelines.
 *
 * @author Xujiayao
 */
public final class MessageParserCommon {

	/**
	 * Builds the mention notification shown to a mentioned Minecraft player.
	 *
	 * @param senderDisplayName Display name of the mention sender.
	 * @return The localized notification text.
	 */
	public static String mentionNotification(String senderDisplayName) {
		String template = I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("mentioned")
				.asString("{effective_name} mentioned you!");
		return template.replace("{effective_name}", senderDisplayName);
	}

	/**
	 * Color used for hyperlinks. Matches the blue Discord uses for its own links.
	 */
	static final String URL_COLOR = "#3366CC";

	// --- Inline tokens shared by both directions -------------------------------------------------

	/** {@code <t:EPOCH>} / {@code <t:EPOCH:STYLE>} */
	static final Pattern TIMESTAMP = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");
	/** {@code [label](https://url)} */
	static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\(<?(https?://[^>\\s)]+)>?\\)");
	/** Plain {@code https://url} that is not already part of a link or an emphasis run. */
	static final Pattern BARE_URL = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
	/** A single Unicode emoji (including ZWJ sequences and variation selectors). */
	static final Pattern UNICODE_EMOJI = Pattern.compile(
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
	/** Discord's {@code :alias:} emoji syntax. */
	static final Pattern ALIAS_EMOJI = Pattern.compile("(?<![A-Za-z0-9_]):([A-Za-z0-9_+\\-]+):(?![A-Za-z0-9_])");
	/** Discord's {@code <:name:id>} / {@code <a:name:id>} custom emoji token. */
	static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:(\\w+):\\d+>");

	// --- Mention tokens --------------------------------------------------------------------------

	/** {@code <@123>} / {@code <@!123>} */
	static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
	/** {@code <@&123>} */
	static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
	/** {@code <#123>} */
	static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
	/** {@code @everyone} / {@code @here} */
	static final Pattern EVERYONE_HERE = Pattern.compile("@(everyone|here)");

	/** Spoiler-wrapped mention tokens. Only used when Markdown parsing is off, because the Markdown
	 *  scanner already unwraps {@code ||...||} by itself. */
	static final Pattern SPOILER_USER_MENTION = Pattern.compile("\\|\\|<@!?(\\d+)>\\|\\|");
	static final Pattern SPOILER_ROLE_MENTION = Pattern.compile("\\|\\|<@&(\\d+)>\\|\\|");
	static final Pattern SPOILER_CHANNEL_MENTION = Pattern.compile("\\|\\|<#(\\d+)>\\|\\|");
	static final Pattern SPOILER_EVERYONE_HERE = Pattern.compile("\\|\\|@(everyone|here)\\|\\|");
	/** Any {@code ||spoiler||} run, used to detect spoiler-wrapped embed URLs. */
	static final Pattern SPOILER_CONTENT = Pattern.compile("\\|\\|(.+?)\\|\\|");

	// --- Line decorations ------------------------------------------------------------------------

	private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+\\S.*$");

	private MessageParserCommon() {
	}

	/**
	 * Rewrites a matched token inside every segment of {@code segments}.
	 * <p>
	 * This replaces the handful of near-identical {@code splitSegmentsByXxx} methods that used to exist
	 * for user/role/channel/everyone mentions, custom emoji, alias emoji, links and timestamps. Segments
	 * that already carry a click URL are never rewritten, and text outside the matches is carried over
	 * with the original styling.
	 */
	@FunctionalInterface
	interface TokenStyler {
		/**
		 * Builds the replacement for one match.
		 *
		 * @param matcher The matcher positioned on the match.
		 * @param source  The segment the match was found in, used to inherit its styling.
		 * @return The styled replacement, or null to leave this match as literal text.
		 */
		TextSegment style(Matcher matcher, TextSegment source);
	}

	/**
	 * @param segment Candidate segment.
	 * @return Whether inline tokens should be searched inside this segment.
	 */
	static boolean isSplittable(TextSegment segment) {
		return segment.clickUrl == null && segment.text != null && !segment.text.isEmpty();
	}

	/**
	 * Splits every segment around the matches of {@code pattern}, replacing each match with a styled segment.
	 *
	 * @param segments Source segments.
	 * @param pattern  Token pattern to search for.
	 * @param styler   Replacement factory; returning null leaves the match untouched.
	 * @return The rewritten segment list.
	 */
	static List<TextSegment> splitByPattern(List<TextSegment> segments, Pattern pattern, TokenStyler styler) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (!isSplittable(segment)) {
				out.add(segment);
				continue;
			}
			Matcher matcher = pattern.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				TextSegment styled = styler.style(matcher, segment);
				if (styled == null) {
					continue;
				}
				if (matcher.start() > cursor) {
					out.add(TextSegment.copyOf(segment, segment.text.substring(cursor, matcher.start())));
				}
				out.add(styled);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegment.copyOf(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	/**
	 * One entry of the single-pass token table.
	 *
	 * @param pattern Token pattern.
	 * @param styler  Replacement factory.
	 */
	record TokenRule(Pattern pattern, TokenStyler styler) {
	}

	/**
	 * Runs a token table in a single left-to-right pass.
	 * <p>
	 * Unlike {@link #splitByPattern}, text produced by one rule is never rescanned by the following rules.
	 * That matters when several token kinds can appear in the same string: a spoiler-wrapped mention has to
	 * win over the plain mention inside it, and a rendered {@code [@everyone]} must not be matched again by
	 * the {@code @everyone} rule. At the same start position the earlier rule wins.
	 *
	 * @param segments Source segments.
	 * @param rules    Token rules in priority order.
	 * @return The rewritten segment list.
	 */
	static List<TextSegment> splitByRules(List<TextSegment> segments, List<TokenRule> rules) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (!isSplittable(segment) || rules.isEmpty()) {
				out.add(segment);
				continue;
			}
			String text = segment.text;
			List<Matcher> matchers = new ArrayList<>(rules.size());
			for (TokenRule rule : rules) {
				// Transparent bounds keep look-behind assertions working across the region boundary.
				matchers.add(rule.pattern().matcher(text).useTransparentBounds(true));
			}

			int cursor = 0;
			int i = 0;
			while (i < text.length()) {
				boolean matched = false;
				for (int r = 0; r < rules.size(); r++) {
					Matcher matcher = matchers.get(r).region(i, text.length());
					if (!matcher.lookingAt()) {
						continue;
					}
					TextSegment styled = rules.get(r).styler().style(matcher, segment);
					if (styled == null) {
						continue;
					}
					if (i > cursor) {
						out.add(TextSegment.copyOf(segment, text.substring(cursor, i)));
					}
					out.add(styled);
					i = matcher.end();
					cursor = i;
					matched = true;
					break;
				}
				if (!matched) {
					i++;
				}
			}

			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < text.length()) {
				out.add(TextSegment.copyOf(segment, text.substring(cursor)));
			}
		}
		return out;
	}

	/**
	 * Builds a hyperlink segment inheriting the styling of the segment it was found in.
	 */
	static TextSegment link(TextSegment source, String text, String url) {
		TextSegment segment = TextSegment.copyOf(source, text);
		segment.clickUrl = url;
		segment.underlined = true;
		segment.color = URL_COLOR;
		segment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
		return segment;
	}

	/**
	 * Builds a label segment that opens {@code url} when clicked.
	 */
	private static TextSegment label(String text, String color, String url) {
		TextSegment segment = new TextSegment(text, false, color);
		if (url != null) {
			segment.underlined = true;
			segment.clickUrl = url;
			segment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
		}
		return segment;
	}

	/**
	 * Builds the clickable attachment label {@code <attachment type=[x] name=[y]>}.
	 */
	static List<TextSegment> attachment(String type, String fileName, String url, boolean spoiler) {
		List<TextSegment> segments = new ArrayList<>();
		segments.add(label("<attachment type=[" + type + "] name=[", URL_COLOR, url));
		TextSegment name = label(fileName, URL_COLOR, url);
		if (spoiler) {
			name.obfuscated = true;
			name.hoverText = fileName;
		}
		segments.add(name);
		segments.add(label("]>", URL_COLOR, url));
		return segments;
	}

	/**
	 * Builds the clickable embed label {@code <embed title=[x]>}.
	 */
	static List<TextSegment> embed(String title, String url, boolean spoiler) {
		String color = url == null ? "yellow" : URL_COLOR;
		List<TextSegment> segments = new ArrayList<>();
		segments.add(label("<embed title=[", color, url));
		TextSegment body = label(title, color, url);
		if (spoiler) {
			body.obfuscated = true;
			body.hoverText = title;
		}
		segments.add(body);
		segments.add(label("]>", color, url));
		return segments;
	}

	/**
	 * Marks a segment as an obfuscated spoiler.
	 * <p>
	 * Obfuscated Minecraft text is unreadable in chat, so the original plain text is kept as hover preview.
	 */
	static TextSegment spoiler(TextSegment segment) {
		segment.obfuscated = true;
		segment.hoverText = segment.text;
		return segment;
	}

	// --- Line classification ---------------------------------------------------------------------

	static boolean isUnderscoreDelimiter(String delimiter) {
		return "_".equals(delimiter) || "__".equals(delimiter);
	}

	static boolean isMarkdownHeadingLine(String line) {
		return line != null && !line.isEmpty() && HEADING_LINE.matcher(line.stripLeading()).matches();
	}

	static boolean isMarkdownQuoteLine(String line) {
		if (line == null || line.isEmpty()) {
			return false;
		}
		String stripped = line.stripLeading();
		return !stripped.isEmpty() && stripped.charAt(0) == '>';
	}

	/**
	 * @return Whether the character at {@code index} sits inside a {@code :alias:} emoji token, in which
	 * case an underscore must not be treated as a Markdown delimiter.
	 */
	static boolean isInsideAliasEmoji(String text, int index) {
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
		return ALIAS_EMOJI.matcher(text.substring(leftColon, rightColon + 1)).matches();
	}

	// --- Discord timestamps ----------------------------------------------------------------------

	/**
	 * Renders a Discord timestamp token value using the locale configured for DMCC.
	 *
	 * @param epoch Epoch seconds.
	 * @param style Discord style letter ({@code t T d D f F R s S}), defaulting to {@code f}.
	 * @return The human readable timestamp.
	 */
	static String formatDiscordTimestamp(long epoch, String style) {
		Instant instant = Instant.ofEpochSecond(epoch);
		Locale locale = dmccLocale();
		ZoneId zone = ZoneId.systemDefault();
		String effectiveStyle = style == null ? "f" : style;

		return switch (effectiveStyle) {
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
			case "R" -> formatRelativeTime(Instant.now().getEpochSecond() - epoch);
			default -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
		};
	}

	/**
	 * Replaces every Discord timestamp token in {@code text} with its human readable form.
	 *
	 * @param text Source text that may contain {@code <t:...>} tokens.
	 * @return Text with timestamp tokens replaced, or the input unchanged when it is null or empty.
	 */
	static String formatTimestampsForPlainText(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		Matcher matcher = TIMESTAMP.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String replacement = matcher.group();
			try {
				replacement = "[" + formatDiscordTimestamp(Long.parseLong(matcher.group(1)), matcher.group(2)) + "]";
			} catch (Exception ignored) {
				// Not a usable epoch value: keep the raw token.
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/**
	 * @return The timestamp token rendered as {@code [value]}, or the raw token when it cannot be parsed.
	 */
	static TextSegment timestamp(Matcher matcher, TextSegment source) {
		String timestamp;
		try {
			timestamp = "[" + formatDiscordTimestamp(Long.parseLong(matcher.group(1)), matcher.group(2)) + "]";
		} catch (Exception ignored) {
			timestamp = matcher.group();
		}
		TextSegment segment = TextSegment.copyOf(source, timestamp);
		segment.color = "yellow";
		return segment;
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

		String unit = I18nManager.getDmccTranslation(String.format(
				"discord.message_parser.relative.units.%s.%s", unitKey, value == 1 ? "one" : "other"));
		return past
				? I18nManager.getDmccTranslation("discord.message_parser.relative.past", value, unit)
				: I18nManager.getDmccTranslation("discord.message_parser.relative.future", value, unit);
	}

	private static Locale dmccLocale() {
		String languageCode = I18nManager.getLanguage();
		if (languageCode == null || languageCode.isBlank()) {
			return Locale.ENGLISH;
		}
		Locale locale = Locale.forLanguageTag(languageCode.replace('_', '-'));
		return locale.getLanguage().isBlank() ? Locale.ENGLISH : locale;
	}

	/**
	 * Replaces every Unicode emoji with its first Discord alias.
	 */
	static TextSegment unicodeEmoji(Matcher matcher, TextSegment source) {
		String alias = EmojiManager.replaceAllEmojis(matcher.group(), emoji -> emoji.getDiscordAliases().getFirst());
		TextSegment segment = TextSegment.copyOf(source, alias);
		segment.color = "yellow";
		return segment;
	}
}
