package com.xujiayao.discord_mc_chat.utils.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility helpers for working with {@link TextSegment} collections.
 *
 * @author Xujiayao
 */
public final class TextSegmentUtils {

	private TextSegmentUtils() {
	}

	public static TextSegment copySegment(TextSegment source, String text) {
		TextSegment copy = new TextSegment(text, source.bold, source.color);
		copy.italic = source.italic;
		copy.underlined = source.underlined;
		copy.strikethrough = source.strikethrough;
		copy.obfuscated = source.obfuscated;
		copy.clickUrl = source.clickUrl;
		copy.hoverText = source.hoverText;
		return copy;
	}

	public static List<TextSegment> copySegments(List<TextSegment> segments) {
		List<TextSegment> copy = new ArrayList<>();
		for (TextSegment segment : segments) {
			copy.add(copySegment(segment, segment.text));
		}
		return copy;
	}

	public static void applyDefaultColor(List<TextSegment> segments, String defaultColor) {
		if (defaultColor == null || defaultColor.isEmpty()) {
			return;
		}
		for (TextSegment segment : segments) {
			if (segment.color == null || segment.color.isEmpty()) {
				segment.color = defaultColor;
			}
		}
	}

	public static void appendEllipsis(List<TextSegment> segments) {
		if (segments.isEmpty()) {
			segments.add(new TextSegment("..."));
			return;
		}
		TextSegment tail = segments.getLast();
		segments.set(segments.size() - 1, copySegment(tail, tail.text + "..."));
	}
}
