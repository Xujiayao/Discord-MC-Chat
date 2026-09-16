package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.network.message.TextSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown scanner shared by both directions of the chat bridge.
 * <p>
 * Discord and Minecraft chat do not use identical emphasis rules, so the two scanners below are kept
 * separate on purpose:
 * <ul>
 *   <li>{@link #parseDiscordMarkup(String)} consumes backslash escapes and treats every delimiter as a
 *       <em>paired</em> token that has to be closed on the same line before it takes effect.</li>
 *   <li>{@link #parseMinecraftMarkup(String)} keeps backslashes verbatim (players type Windows paths and
 *       the like) and lets an unclosed delimiter stay active until its matching delimiter appears, which
 *       is how players format multi-line messages.</li>
 * </ul>
 * Everything else - delimiter matching, closing searches, style bookkeeping and segment emission - is
 * shared.
 *
 * @author Xujiayao
 */
final class MarkdownParser {

	/**
	 * Color applied to Markdown quote lines.
	 */
	static final String QUOTE_COLOR = "gray";

	/**
	 * Ordered longest-first so that {@code ***} wins over {@code **} and {@code *}.
	 */
	private static final List<String> DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");

	private MarkdownParser() {
	}

	/**
	 * Parses Discord message content into styled segments.
	 * <p>
	 * Emphasis never carries across lines: Discord requires the closing delimiter on the same line, so an
	 * unclosed delimiter stays literal. Quote lines are rendered gray and heading lines bold.
	 *
	 * @param text Raw Discord message content.
	 * @return Styled segments.
	 */
	static List<TextSegment> parseDiscordMarkup(String text) {
		List<TextSegment> out = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int newline = text.indexOf('\n', start);
			int lineEnd = newline >= 0 ? newline : text.length();
			String line = text.substring(start, lineEnd);

			State state = new State();
			decorateLine(state, line);
			out.addAll(scanDiscord(line, state));

			if (newline < 0) {
				break;
			}
			out.add(new TextSegment("\n"));
			start = newline + 1;
		}
		return out;
	}

	/**
	 * Parses Minecraft chat text into styled segments.
	 * <p>
	 * Emphasis deliberately survives line breaks so that a player can open {@code *italic} on one line and
	 * close it on a later one. Quote lines are rendered gray and heading lines bold.
	 *
	 * @param raw Raw Minecraft chat text.
	 * @return Styled segments. Empty input yields a single empty segment, matching the historical contract.
	 */
	static List<TextSegment> parseMinecraftMarkup(String raw) {
		if (raw.isEmpty()) {
			return List.of(new TextSegment(""));
		}
		List<TextSegment> out = new ArrayList<>();
		State carried = new State();
		int start = 0;
		while (start < raw.length()) {
			int newline = raw.indexOf('\n', start);
			int lineEnd = newline >= 0 ? newline : raw.length();
			String line = raw.substring(start, lineEnd);

			State state = carried.copy();
			List<TextSegment> lineSegments = scanMinecraft(line, state);
			// Quote/heading decoration is applied after scanning here: a heading sets bold on top of
			// whatever the line produced, whereas feeding it in as scanner state would let an active
			// bold delimiter turn it back off.
			decorateSegments(line, lineSegments);
			out.addAll(lineSegments);

			carried.bold = state.bold;
			carried.italic = state.italic;
			carried.underlined = state.underlined;
			carried.strikethrough = state.strikethrough;
			carried.obfuscated = state.obfuscated;

			if (newline < 0) {
				break;
			}
			out.add(new TextSegment("\n"));
			start = newline + 1;
		}
		return out;
	}

	/**
	 * Discord scanner: recursive, escapes are consumed, delimiters must be closed.
	 */
	private static List<TextSegment> scanDiscord(String text, State state) {
		List<TextSegment> out = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			char current = text.charAt(i);

			if (current == '\\' && i + 1 < text.length()) {
				plain.append(text.charAt(i + 1));
				i += 2;
				continue;
			}
			if (current == '`') {
				int close = findClosingDelimiter(text, i + 1, "`", true);
				if (close > i) {
					flush(out, plain, state);
					addStyled(out, "[" + text.substring(i + 1, close) + "]", state);
					i = close + 1;
					continue;
				}
			}
			String delimiter = matchDelimiter(text, i);
			if (delimiter != null) {
				int close = findClosingDelimiter(text, i + delimiter.length(), delimiter, true);
				if (close > i) {
					flush(out, plain, state);
					State nested = state.copy();
					applyStyle(nested, delimiter, false);
					out.addAll(scanDiscord(text.substring(i + delimiter.length(), close), nested));
					i = close + delimiter.length();
					continue;
				}
			}

			plain.append(current);
			i++;
		}
		flush(out, plain, state);
		return out;
	}

	/**
	 * Minecraft scanner: serial, backslashes are kept, an active delimiter toggles its style off.
	 */
	private static List<TextSegment> scanMinecraft(String text, State state) {
		List<TextSegment> out = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			String delimiter = consumableDelimiter(state, text, i);
			if (delimiter != null) {
				flush(out, plain, state);
				applyStyle(state, delimiter, true);
				i += delimiter.length();
				continue;
			}
			plain.append(text.charAt(i));
			i++;
		}
		flush(out, plain, state);
		return out;
	}

	/**
	 * Minecraft rules only open a delimiter when it is already active (so it closes) or when a matching
	 * delimiter follows on the same line. A delimiter that fails both tests is skipped so that the next
	 * (shorter) candidate at the same position gets a chance - that is what makes {@code __} fall back to
	 * {@code _} instead of staying literal.
	 *
	 * @return The delimiter to consume at {@code index}, or null when the character is plain text.
	 */
	private static String consumableDelimiter(State state, String text, int index) {
		for (String delimiter : DELIMITERS) {
			if (!text.startsWith(delimiter, index)) {
				continue;
			}
			if (MessageParserCommon.isUnderscoreDelimiter(delimiter)
					&& MessageParserCommon.isInsideAliasEmoji(text, index)) {
				continue;
			}
			if (isStyleActive(state, delimiter)
					|| findClosingDelimiter(text, index + delimiter.length(), delimiter, false) >= 0) {
				return delimiter;
			}
		}
		return null;
	}

	/**
	 * Applies the quote/heading decoration a whole line contributes, used by the Discord scanner which
	 * feeds it in as base state.
	 */
	private static void decorateLine(State state, String line) {
		if (MessageParserCommon.isMarkdownQuoteLine(line)) {
			state.color = QUOTE_COLOR;
		}
		if (MessageParserCommon.isMarkdownHeadingLine(line)) {
			state.bold = true;
		}
	}

	/**
	 * Applies the quote/heading decoration on top of already scanned line segments.
	 */
	private static void decorateSegments(String line, List<TextSegment> lineSegments) {
		if (lineSegments.isEmpty()) {
			return;
		}
		boolean quote = MessageParserCommon.isMarkdownQuoteLine(line);
		boolean heading = MessageParserCommon.isMarkdownHeadingLine(line);
		if (!quote && !heading) {
			return;
		}
		for (TextSegment segment : lineSegments) {
			if (quote) {
				segment.color = QUOTE_COLOR;
			}
			if (heading) {
				segment.bold = true;
			}
		}
	}

	/**
	 * @return The Markdown delimiter starting at {@code index}, or null when there is none.
	 */
	private static String matchDelimiter(String text, int index) {
		for (String delimiter : DELIMITERS) {
			if (!text.startsWith(delimiter, index)) {
				continue;
			}
			if (MessageParserCommon.isUnderscoreDelimiter(delimiter)
					&& MessageParserCommon.isInsideAliasEmoji(text, index)) {
				continue;
			}
			return delimiter;
		}
		return null;
	}

	/**
	 * Finds the next occurrence of {@code delimiter} that is not backslash-escaped.
	 *
	 * @param text           Text to scan.
	 * @param start          Index to start searching at.
	 * @param delimiter      Delimiter to look for.
	 * @param skipAliasEmoji Whether occurrences inside a {@code :alias:} emoji token are ignored. Discord
	 *                       needs this because underscores are common inside emoji names; the Minecraft
	 *                       scanner historically did not do it, and changing that would alter how existing
	 *                       player messages render, so the difference is kept explicit.
	 * @return The index of the closing delimiter, or -1 when there is none.
	 */
	private static int findClosingDelimiter(String text, int start, String delimiter, boolean skipAliasEmoji) {
		for (int i = start; i <= text.length() - delimiter.length(); i++) {
			if (text.charAt(i) == '\\') {
				i++;
				continue;
			}
			if (skipAliasEmoji && MessageParserCommon.isUnderscoreDelimiter(delimiter)
					&& MessageParserCommon.isInsideAliasEmoji(text, i)) {
				continue;
			}
			if (text.startsWith(delimiter, i)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isStyleActive(State state, String delimiter) {
		return switch (delimiter) {
			case "***" -> state.bold && state.italic;
			case "**" -> state.bold;
			case "*", "_" -> state.italic;
			case "__" -> state.underlined;
			case "~~" -> state.strikethrough;
			case "||" -> state.obfuscated;
			default -> false;
		};
	}

	/**
	 * @param toggle Whether the delimiter flips its style instead of turning it on.
	 */
	private static void applyStyle(State state, String delimiter, boolean toggle) {
		switch (delimiter) {
			case "***" -> {
				state.bold = toggle ? !state.bold : true;
				state.italic = toggle ? !state.italic : true;
			}
			case "**" -> state.bold = toggle ? !state.bold : true;
			case "*", "_" -> state.italic = toggle ? !state.italic : true;
			case "__" -> state.underlined = toggle ? !state.underlined : true;
			case "~~" -> state.strikethrough = toggle ? !state.strikethrough : true;
			case "||" -> state.obfuscated = toggle ? !state.obfuscated : true;
			default -> {
			}
		}
	}

	private static void flush(List<TextSegment> segments, StringBuilder plain, State state) {
		if (plain.isEmpty()) {
			return;
		}
		addStyled(segments, plain.toString(), state);
		plain.setLength(0);
	}

	private static void addStyled(List<TextSegment> segments, String text, State state) {
		if (text.isEmpty()) {
			return;
		}
		TextSegment segment = new TextSegment(text);
		segment.bold = state.bold;
		segment.italic = state.italic;
		segment.underlined = state.underlined;
		segment.strikethrough = state.strikethrough;
		segment.obfuscated = state.obfuscated;
		segment.color = state.color;
		if (segment.obfuscated) {
			// Obfuscated text is unreadable, so keep the plain text as hover preview.
			segment.hoverText = text;
		}
		segments.add(segment);
	}

	/**
	 * Emphasis state carried while scanning.
	 */
	static final class State {
		boolean bold;
		boolean italic;
		boolean underlined;
		boolean strikethrough;
		boolean obfuscated;
		String color;

		State copy() {
			State copy = new State();
			copy.bold = bold;
			copy.italic = italic;
			copy.underlined = underlined;
			copy.strikethrough = strikethrough;
			copy.obfuscated = obfuscated;
			copy.color = color;
			return copy;
		}
	}
}
