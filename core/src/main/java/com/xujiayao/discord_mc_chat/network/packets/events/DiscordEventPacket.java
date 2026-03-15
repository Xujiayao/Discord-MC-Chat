package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent from DMCC Server to Minecraft Client(s) containing a Discord event.
 * <p>
 * Carries pre-parsed message segments so that the client can render rich text
 * in Minecraft without requiring JDA or Discord API access.
 *
 * @author Xujiayao
 */
public class DiscordEventPacket extends Packet {

	public EventType type;

	// ===== Chat message data =====

	/** The effective display name of the Discord user. */
	public String effectiveName;

	/** The hex color string of the user's top role (e.g. "#FF0000"), or null if no colored role. */
	public String roleColor;

	/** The parsed message content as a list of styled text segments. */
	public List<TextSegment> segments;

	// ===== Reply context =====

	/** Whether this message is a reply to another message. */
	public boolean hasReply;

	/** The effective display name of the replied-to user. */
	public String replyEffectiveName;

	/** The hex color string of the replied-to user's top role, or null. */
	public String replyRoleColor;

	/** The parsed content of the replied-to message. */
	public List<TextSegment> replySegments;

	// ===== Mention notification data =====

	/** UUIDs of Minecraft players who were mentioned (via linked Discord accounts). */
	public List<String> mentionedMinecraftUuids;

	// ===== Command notification data =====

	/** The name of the slash command that was executed. */
	public String commandName;

	/**
	 * Constructs a CHAT type Discord event packet.
	 *
	 * @param effectiveName         The display name of the Discord user.
	 * @param roleColor             The hex color of the user's top role.
	 * @param segments              The parsed message segments.
	 * @param hasReply              Whether this message is a reply.
	 * @param replyEffectiveName    The display name of the replied-to user.
	 * @param replyRoleColor        The hex color of the replied-to user's role.
	 * @param replySegments         The parsed segments of the replied-to message.
	 * @param mentionedMinecraftUuids UUIDs of mentioned Minecraft players.
	 */
	public DiscordEventPacket(String effectiveName, String roleColor,
							  List<TextSegment> segments,
							  boolean hasReply, String replyEffectiveName,
							  String replyRoleColor, List<TextSegment> replySegments,
							  List<String> mentionedMinecraftUuids) {
		this.type = EventType.CHAT;
		this.effectiveName = effectiveName;
		this.roleColor = roleColor;
		this.segments = segments;
		this.hasReply = hasReply;
		this.replyEffectiveName = replyEffectiveName;
		this.replyRoleColor = replyRoleColor;
		this.replySegments = replySegments;
		this.mentionedMinecraftUuids = mentionedMinecraftUuids;
	}

	/**
	 * Constructs a COMMAND type Discord event packet.
	 *
	 * @param effectiveName The display name of the Discord user.
	 * @param roleColor     The hex color of the user's top role.
	 * @param commandName   The slash command name that was executed.
	 */
	public DiscordEventPacket(String effectiveName, String roleColor, String commandName) {
		this.type = EventType.COMMAND;
		this.effectiveName = effectiveName;
		this.roleColor = roleColor;
		this.commandName = commandName;
		this.segments = new ArrayList<>();
		this.mentionedMinecraftUuids = new ArrayList<>();
	}

	/**
	 * Enum representing the type of Discord event.
	 */
	public enum EventType {
		/** A chat message from a Discord channel. */
		CHAT,
		/** A slash command execution notification. */
		COMMAND
	}

	/**
	 * A styled text segment for rendering rich Discord content in Minecraft.
	 * <p>
	 * Each segment represents a piece of text with optional formatting, color,
	 * click event (URL), and hover text.
	 */
	public static class TextSegment implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		/** The text content of this segment. */
		public String text;

		/** The color of this segment (hex string like "#FFAA00", or named Minecraft color). Null means inherit. */
		public String color;

		/** Whether this segment is bold. */
		public boolean bold;

		/** Whether this segment is italic. */
		public boolean italic;

		/** Whether this segment is underlined. */
		public boolean underlined;

		/** Whether this segment has strikethrough. */
		public boolean strikethrough;

		/** If non-null, clicking this segment opens this URL. */
		public String clickUrl;

		/** If non-null, hovering over this segment shows this text. */
		public String hoverText;

		/**
		 * Default constructor for serialization.
		 */
		public TextSegment() {
		}

		/**
		 * Constructs a plain text segment with no special formatting.
		 *
		 * @param text The text content.
		 */
		public TextSegment(String text) {
			this.text = text;
		}

		/**
		 * Constructs a colored text segment.
		 *
		 * @param text  The text content.
		 * @param color The color string.
		 */
		public TextSegment(String text, String color) {
			this.text = text;
			this.color = color;
		}

		/**
		 * Creates a new TextSegment as a builder-style copy with a click URL.
		 *
		 * @param url The URL to open on click.
		 * @return This segment for chaining.
		 */
		public TextSegment withClickUrl(String url) {
			this.clickUrl = url;
			return this;
		}

		/**
		 * Creates a new TextSegment as a builder-style copy with hover text.
		 *
		 * @param hover The text to show on hover.
		 * @return This segment for chaining.
		 */
		public TextSegment withHoverText(String hover) {
			this.hoverText = hover;
			return this;
		}

		/**
		 * Sets the underlined flag.
		 *
		 * @param value Whether to underline.
		 * @return This segment for chaining.
		 */
		public TextSegment withUnderlined(boolean value) {
			this.underlined = value;
			return this;
		}

		/**
		 * Sets the bold flag.
		 *
		 * @param value Whether to bold.
		 * @return This segment for chaining.
		 */
		public TextSegment withBold(boolean value) {
			this.bold = value;
			return this;
		}

		/**
		 * Sets the italic flag.
		 *
		 * @param value Whether to italicize.
		 * @return This segment for chaining.
		 */
		public TextSegment withItalic(boolean value) {
			this.italic = value;
			return this;
		}

		/**
		 * Sets the strikethrough flag.
		 *
		 * @param value Whether to strikethrough.
		 * @return This segment for chaining.
		 */
		public TextSegment withStrikethrough(boolean value) {
			this.strikethrough = value;
			return this;
		}
	}
}
