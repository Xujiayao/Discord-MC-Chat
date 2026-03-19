package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.Map;

/**
 * Packet sent from Minecraft Client to DMCC Server containing an in-game event or message.
 *
 * @author Xujiayao
 */
public class MinecraftEventPacket extends Packet {
	public MessageType type;
	public Map<String, String> placeholders;

	public MinecraftEventPacket(MessageType type, Map<String, String> placeholders) {
		this.type = type;
		this.placeholders = placeholders;
	}

	/**
	 * Enum representing the type of message being sent.
	 *
	 * @author Xujiayao
	 */
	public enum MessageType {
		// Server events
		SERVER_STARTED,
		SERVER_STOPPING,

		// Player events
		PLAYER_JOIN,
		PLAYER_QUIT,
		PLAYER_CHAT,
		PLAYER_COMMAND,
		PLAYER_DIE,
		PLAYER_ADVANCEMENT,
		PLAYER_CHANGE_GAME_MODE,

		// Source events
		SOURCE_SAY,
		SOURCE_TELL_RAW,
		SOURCE_MSG,
		SOURCE_ME
	}
}
