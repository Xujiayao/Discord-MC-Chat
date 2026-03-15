package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Packet sent from DMCC Server to Minecraft Client containing a Discord or cross-server event.
 * <p>
 * Carries pre-formatted styled text segments that the Minecraft client can directly render
 * as styled chat components without needing access to custom_messages configuration.
 *
 * @author Xujiayao
 */
public class DiscordEventPacket extends Packet {

	/**
	 * The type of event this packet represents.
	 */
	public EventType type;

	/**
	 * The main message segments to render in Minecraft chat.
	 * Each segment contains styled text (text, bold, color) that maps to a Minecraft Component.
	 */
	public List<TextSegment> segments;

	/**
	 * Optional reply/response context segments to render above the main message.
	 * Used when a Discord message is a reply to another message.
	 * May be null or empty if not applicable.
	 */
	public List<TextSegment> responseSegments;

	/**
	 * Optional mention notification text to display to mentioned players.
	 * May be null if no players are mentioned.
	 */
	public String mentionNotification;

	/**
	 * List of Minecraft player UUIDs that were mentioned in the Discord message.
	 * Used for action_bar/title/chat notification on the Minecraft side.
	 * May be null or empty if no players are mentioned.
	 */
	public List<String> mentionedPlayerUuids;

	/**
	 * Constructs a DiscordEventPacket with the given type and segments.
	 *
	 * @param type     The event type.
	 * @param segments The styled text segments for the main message.
	 */
	public DiscordEventPacket(EventType type, List<TextSegment> segments) {
		this.type = type;
		this.segments = segments;
	}

	/**
	 * Represents a styled text segment for rendering in Minecraft chat.
	 * Each segment corresponds to a single Component with optional bold and color styling.
	 */
	public static class TextSegment implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * The text content of this segment.
		 */
		public String text;

		/**
		 * Whether this segment should be rendered in bold.
		 */
		public boolean bold;

		/**
		 * The color name for this segment (e.g. "gray", "blue", "dark_gray", "#RRGGBB").
		 */
		public String color;

		/**
		 * Constructs a TextSegment with the given text, bold, and color.
		 *
		 * @param text  The text content.
		 * @param bold  Whether the text is bold.
		 * @param color The color name.
		 */
		public TextSegment(String text, boolean bold, String color) {
			this.text = text;
			this.bold = bold;
			this.color = color;
		}
	}

	/**
	 * Enum representing the type of event carried by this packet.
	 */
	public enum EventType {
		/**
		 * A Discord user sent a message in the monitored channel.
		 */
		DISCORD_CHAT,

		/**
		 * A player on another Minecraft server sent a chat message (cross-server).
		 */
		CROSS_SERVER_CHAT,

		/**
		 * An event occurred on another Minecraft server (e.g. join, quit, die, say, tellraw).
		 */
		CROSS_SERVER_EVENT
	}
}
