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
			/**
			 * Discord chat message relay.
			 */
			CHAT,
			/**
			 * Discord command execution notification relay.
			 */
			COMMAND,
			/**
			 * Discord reaction relay.
			 */
			REACTION,
			/**
			 * Discord message edit relay.
			 */
			EDIT,
			/**
			 * Discord message delete relay.
			 */
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
			/**
			 * Server start event.
			 */
			SERVER_STARTED,
			/**
			 * Server stopping event.
			 */
			SERVER_STOPPING,
			/**
			 * Player join event.
			 */
			PLAYER_JOIN,
			/**
			 * Player quit event.
			 */
			PLAYER_QUIT,
			/**
			 * Player chat event.
			 */
			PLAYER_CHAT,
			/**
			 * Player command event.
			 */
			PLAYER_COMMAND,
			/**
			 * Player death event.
			 */
			PLAYER_DIE,
			/**
			 * Player advancement event.
			 */
			PLAYER_ADVANCEMENT,
			/**
			 * Player game mode change event.
			 */
			PLAYER_CHANGE_GAME_MODE,
			/**
			 * /say source command event.
			 */
			SOURCE_SAY,
			/**
			 * /tellraw source command event.
			 */
			SOURCE_TELL_RAW,
			/**
			 * /msg source command event.
			 */
			SOURCE_MSG,
			/**
			 * /me source command event.
			 */
			SOURCE_ME
		}
	}

	/**
	 * Packet that relays Minecraft-originated messages back to Minecraft clients.
	 */
	public static final class MinecraftRelayPacket extends Packet {
		/**
		 * Relay message type.
		 */
		public MessageType type;
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
		 * @param type     Relay message type.
		 * @param segments Parsed message segments.
		 */
		public MinecraftRelayPacket(MessageType type, List<TextSegment> segments) {
			this.type = type;
			this.segments = segments;
		}

		/**
		 * Message types for Minecraft relays.
		 */
		public enum MessageType {
			/**
			 * Player-originated message.
			 */
			USER_MESSAGE,
			/**
			 * System-originated message.
			 */
			SYSTEM_MESSAGE,
			/**
			 * Command-originated message.
			 */
			COMMAND
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
