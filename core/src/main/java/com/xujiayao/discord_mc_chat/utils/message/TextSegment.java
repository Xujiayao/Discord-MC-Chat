package com.xujiayao.discord_mc_chat.utils.message;

import com.xujiayao.discord_mc_chat.network.packets.EventPackets.DiscordRelayPacket;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Represents a single rich text segment for in-game rendering.
 * <p>
 * Each segment carries its own display text, styling, and optional interaction data.
 * A list of TextSegment objects fully describes a Discord message line that can be
 * rendered as a Minecraft Component on the client side without needing access to
 * Discord APIs or custom_messages.
 * <p>
 * This class is intentionally kept as a simple serializable POJO so that it can be
 * transmitted over the Netty channel inside {@link DiscordRelayPacket}.
 *
 * @author Xujiayao
 */
public final class TextSegment implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * The display text of this segment.
	 */
	public String text;

	/**
	 * Whether the text should be rendered in bold.
	 */
	public boolean bold;

	/**
	 * Whether the text should be rendered in italic.
	 */
	public boolean italic;

	/**
	 * Whether the text should be rendered with underline.
	 */
	public boolean underlined;

	/**
	 * Whether the text should be rendered with strikethrough.
	 */
	public boolean strikethrough;

	/**
	 * Whether the text should be rendered obfuscated.
	 */
	public boolean obfuscated;

	/**
	 * The color of the text.
	 * <p>
	 * Accepts Minecraft named colors (e.g. "gold", "dark_gray") or hex color codes
	 * (e.g. "#3366CC"). A null or empty value means "inherit / default".
	 */
	public String color;

	/**
	 * An optional URL to open when the player clicks this segment.
	 * <p>
	 * When non-null, the segment should be rendered as a clickable link.
	 */
	public String clickUrl;

	/**
	 * An optional hover text displayed when the player hovers over this segment.
	 */
	public String hoverText;

	/**
	 * Creates a plain text segment with no styling.
	 *
	 * @param text The display text.
	 */
	public TextSegment(String text) {
		this.text = text;
	}

	/**
	 * Creates a styled text segment.
	 *
	 * @param text  The display text.
	 * @param bold  Whether the text is bold.
	 * @param color The text color (named or hex).
	 */
	public TextSegment(String text, boolean bold, String color) {
		this.text = text;
		this.bold = bold;
		this.color = color;
	}

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
