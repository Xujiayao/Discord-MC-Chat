package com.xujiayao.discord_mc_chat.network.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single rich text segment for in-game rendering.
 * <p>
 * Each segment carries its own display text, styling, and optional interaction data.
 * A list of TextSegment objects fully describes a Discord message line that can be
 * rendered as a Minecraft Component on the client side without needing access to
 * Discord APIs or custom_messages.
 * <p>
 * This class is intentionally kept as a plain mutable data holder: the parsers build segments field by
 * field, and the network codec serializes the public fields as JSON.
 *
 * @author Xujiayao
 */
public final class TextSegment {

	/**
	 * The display text of this segment.
	 */
	public String text;

	public boolean bold;
	public boolean italic;
	public boolean underlined;
	public boolean strikethrough;
	public boolean obfuscated;

	/**
	 * The color of the text, as a Minecraft named color (e.g. "gold", "dark_gray") or a hex code
	 * (e.g. "#3366CC"). A null or empty value means "inherit / default".
	 */
	public String color;

	/**
	 * An optional URL to open when the player clicks this segment. When non-null, the segment is rendered
	 * as a clickable link.
	 */
	public String clickUrl;

	/**
	 * An optional hover text displayed when the player hovers over this segment.
	 */
	public String hoverText;

	/**
	 * Creates an empty segment for the JSON codec, which fills the public fields afterwards.
	 */
	public TextSegment() {
	}

	/**
	 * Creates a plain text segment with no styling.
	 */
	public TextSegment(String text) {
		this.text = text;
	}

	public TextSegment(String text, boolean bold, String color) {
		this.text = text;
		this.bold = bold;
		this.color = color;
	}

	/**
	 * Copies styling/click/hover metadata from a source segment with a new text value.
	 */
	public static TextSegment copyOf(TextSegment source, String text) {
		TextSegment copy = new TextSegment(text, source.bold, source.color);
		copy.italic = source.italic;
		copy.underlined = source.underlined;
		copy.strikethrough = source.strikethrough;
		copy.obfuscated = source.obfuscated;
		copy.clickUrl = source.clickUrl;
		copy.hoverText = source.hoverText;
		return copy;
	}

	/**
	 * Deep-copies a list of segments.
	 */
	public static List<TextSegment> copyOfAll(List<TextSegment> segments) {
		List<TextSegment> copy = new ArrayList<>();
		for (TextSegment segment : segments) {
			copy.add(copyOf(segment, segment.text));
		}
		return copy;
	}

	/**
	 * Applies a fallback color to segments without an explicit one.
	 */
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

	/**
	 * Appends ellipsis to the tail segment, or inserts one when the list is empty.
	 */
	public static void appendEllipsis(List<TextSegment> segments) {
		if (segments.isEmpty()) {
			segments.add(new TextSegment("..."));
			return;
		}
		TextSegment tail = segments.getLast();
		segments.set(segments.size() - 1, copyOf(tail, tail.text + "..."));
	}

	/**
	 * Concatenates segment text into plain text, skipping null entries.
	 */
	public static String toPlainText(List<TextSegment> segments) {
		if (segments == null || segments.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (TextSegment segment : segments) {
			sb.append(segment == null ? "" : segment.toString());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return text == null ? "" : text;
	}
}
