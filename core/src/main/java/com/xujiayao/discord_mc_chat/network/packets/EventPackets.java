package com.xujiayao.discord_mc_chat.network.packets;

import com.xujiayao.discord_mc_chat.network.message.TextSegment;

import java.util.List;
import java.util.Map;

/**
 * Event and relay packet group.
 *
 * @author Xujiayao
 */
public final class EventPackets {
	private EventPackets() {
	}

	/**
	 * Packet that relays parsed Discord events to Minecraft side.
	 */
	public static final class DiscordRelayPacket extends Packet {
		/**
		 * Discord event type.
		 */
		public EventType type;
		/**
		 * Main message segments.
		 */
		public List<TextSegment> segments;
		/**
		 * Reply/reference message segments.
		 */
		public List<TextSegment> replySegments;
		/**
		 * Mention notification text.
		 */
		public String mentionNotificationText;
		/**
		 * Mention notification style.
		 */
		public String mentionNotificationStyle;
		/**
		 * Mentioned player UUIDs.
		 */
		public List<String> mentionedPlayerUuids;
		/**
		 * Whether @everyone was detected.
		 */
		public boolean mentionEveryone;
		/**
		 * Edited message segments for edit events.
		 */
		public List<TextSegment> editedMessageSegments;

		/**
		 * Creates a Discord relay packet.
		 *
		 * @param type     Discord event type.
		 * @param segments Main message segments.
		 */
		public DiscordRelayPacket(EventType type, List<TextSegment> segments) {
			this.type = type;
			this.segments = segments;
		}

		/**
		 * Event types relayed from Discord to Minecraft.
		 */
		public enum EventType {
			CHAT,
			COMMAND,
			REACTION,
			EDIT,
			DELETE
		}
	}

	/**
	 * Packet carrying Minecraft event placeholders for Discord-side templating.
	 */
	public static final class MinecraftEventPacket extends Packet {
		/**
		 * Minecraft event type.
		 */
		public MessageType type;
		/**
		 * Placeholder map used for rendering message templates.
		 */
		public Map<String, String> placeholders;

		/**
		 * Creates a Minecraft event packet.
		 *
		 * @param type         Minecraft event type.
		 * @param placeholders Placeholder map used for rendering message templates.
		 */
		public MinecraftEventPacket(MessageType type, Map<String, String> placeholders) {
			this.type = type;
			this.placeholders = placeholders;
		}

		/**
		 * Supported Minecraft event types for Discord broadcast templates.
		 */
		public enum MessageType {
			SERVER_STARTED,
			SERVER_STOPPING,
			PLAYER_JOIN,
			PLAYER_QUIT,
			PLAYER_CHAT,
			PLAYER_COMMAND,
			PLAYER_DIE,
			PLAYER_ADVANCEMENT,
			PLAYER_CHANGE_GAME_MODE,
			SOURCE_SAY,
			SOURCE_TELL_RAW,
			SOURCE_MSG,
			SOURCE_ME
		}
	}

	/**
	 * Packet that relays Minecraft-originated messages back to Minecraft clients.
	 */
	public static final class MinecraftRelayPacket extends Packet {
		/**
		 * Parsed message segments.
		 */
		public List<TextSegment> segments;
		/**
		 * Raw component JSON for tellraw relay.
		 */
		public String componentJson;
		/**
		 * Placeholder token to replace with component text.
		 */
		public String componentPlaceholder;
		/**
		 * Plain text generated from component JSON.
		 */
		public String componentText;
		/**
		 * Mention notification text.
		 */
		public String mentionNotificationText;
		/**
		 * Mention notification style.
		 */
		public String mentionNotificationStyle;
		/**
		 * Mentioned player UUIDs.
		 */
		public List<String> mentionedPlayerUuids;
		/**
		 * Whether @everyone was detected.
		 */
		public boolean mentionEveryone;

		/**
		 * Creates a Minecraft relay packet.
		 *
		 * @param segments Parsed message segments.
		 */
		public MinecraftRelayPacket(List<TextSegment> segments) {
			this.segments = segments;
		}
	}

	/**
	 * Packet carrying a batch of console log lines.
	 */
	public static final class ConsoleLogBatchPacket extends Packet {
		/**
		 * Log lines in this batch.
		 */
		public List<String> lines;

		/**
		 * Creates a console log batch packet.
		 *
		 * @param lines Log lines in this batch.
		 */
		public ConsoleLogBatchPacket(List<String> lines) {
			this.lines = lines;
		}
	}
}
