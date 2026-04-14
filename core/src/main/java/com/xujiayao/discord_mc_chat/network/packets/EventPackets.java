package com.xujiayao.discord_mc_chat.network.packets;

import com.xujiayao.discord_mc_chat.utils.message.TextSegment;

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

	public static final class DiscordRelayPacket extends Packet {
		public EventType type;
		public List<TextSegment> segments;
		public List<TextSegment> replySegments;
		public String mentionNotificationText;
		public String mentionNotificationStyle;
		public List<String> mentionedPlayerUuids;
		public boolean mentionEveryone;
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
		public MessageType type;
		public Map<String, String> placeholders;

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
		public MessageType type;
		public List<TextSegment> segments;
		public String componentJson;
		public String componentPlaceholder;
		public String componentText;
		public String mentionNotificationText;
		public String mentionNotificationStyle;
		public List<String> mentionedPlayerUuids;
		public boolean mentionEveryone;

		public MinecraftRelayPacket(MessageType type, List<TextSegment> segments) {
			this.type = type;
			this.segments = segments;
		}

		public enum MessageType {
			USER_MESSAGE,
			SYSTEM_MESSAGE,
			COMMAND
		}
	}

	public static final class ConsoleLogBatchPacket extends Packet {
		public List<String> lines;

		public ConsoleLogBatchPacket(List<String> lines) {
			this.lines = lines;
		}
	}
}
