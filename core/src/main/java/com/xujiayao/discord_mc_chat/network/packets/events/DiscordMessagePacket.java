package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Packet sent from DMCC Server to Clients carrying a Discord message
 * to be displayed in Minecraft.
 * <p>
 * Contains pre-formatted text parts resolved from custom_messages templates,
 * so clients can directly build Minecraft Components without needing the config.
 *
 * @author Xujiayao
 */
public class DiscordMessagePacket extends Packet {

	/**
	 * Pre-formatted text parts for the main message line.
	 */
	public List<TextPart> mainParts;

	/**
	 * Pre-formatted text parts for the reply line (null if not a reply).
	 */
	public List<TextPart> replyParts;

	/**
	 * UUIDs of Minecraft players who should receive a mention notification.
	 */
	public List<String> mentionedPlayerUuids;

	/**
	 * The notification style for mentions: "action_bar", "title", or "chat".
	 */
	public String mentionNotificationStyle;

	/**
	 * The name of the sender for mention notifications (e.g., "Xujiayao").
	 */
	public String mentionNotificationSenderName;

	public DiscordMessagePacket(List<TextPart> mainParts, List<TextPart> replyParts,
								List<String> mentionedPlayerUuids, String mentionNotificationStyle,
								String mentionNotificationSenderName) {
		this.mainParts = mainParts;
		this.replyParts = replyParts;
		this.mentionedPlayerUuids = mentionedPlayerUuids;
		this.mentionNotificationStyle = mentionNotificationStyle;
		this.mentionNotificationSenderName = mentionNotificationSenderName;
	}

	/**
	 * A single styled text part that maps to a Minecraft text Component.
	 */
	public static class TextPart implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		public String text;
		public boolean bold;
		public boolean italic;
		public boolean underlined;
		public boolean strikethrough;
		public String color;

		/**
		 * Click event action: "open_url", "suggest_command", "run_command", "copy_to_clipboard", or null.
		 */
		public String clickAction;
		/**
		 * Click event value (e.g., the URL to open or the command to suggest).
		 */
		public String clickValue;

		/**
		 * Hover event text to show when hovering over this part, or null.
		 */
		public String hoverText;

		public TextPart() {
		}

		public TextPart(String text, boolean bold, String color) {
			this.text = text;
			this.bold = bold;
			this.color = color;
		}
	}
}
