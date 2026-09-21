package com.xujiayao.discord_mc_chat.network.packets;

import com.xujiayao.discord_mc_chat.network.message.TextSegment;

import java.util.List;
import java.util.Map;

public final class EventPackets {
	private EventPackets() {
	}

	public static final class DiscordRelayPacket extends Packet {
		/**
		 * Discord event type.
		 */
		public final EventType type;
		/**
		 * Main message segments.
		 */
		public final List<TextSegment> segments;
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

		public DiscordRelayPacket(EventType type, List<TextSegment> segments) {
			this.type = type;
			this.segments = segments;
		}

		public enum EventType {
			CHAT,
			COMMAND,
			REACTION,
			EDIT,
			DELETE
		}
	}

	public static final class MinecraftEventPacket extends Packet {
		/**
		 * Minecraft event type.
		 */
		public final MessageType type;
		/**
		 * Placeholder map used for rendering message templates.
		 */
		public final Map<String, String> placeholders;

		public MinecraftEventPacket(MessageType type, Map<String, String> placeholders) {
			this.type = type;
			this.placeholders = placeholders;
		}

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

	public static final class MinecraftRelayPacket extends Packet {
		/**
		 * Parsed message segments.
		 */
		public final List<TextSegment> segments;
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

		public MinecraftRelayPacket(List<TextSegment> segments) {
			this.segments = segments;
		}
	}

	public static final class ConsoleLogBatchPacket extends Packet {
		/**
		 * Log lines in this batch.
		 */
		public final List<String> lines;

		public ConsoleLogBatchPacket(List<String> lines) {
			this.lines = lines;
		}
	}
}
