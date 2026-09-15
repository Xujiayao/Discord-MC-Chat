package com.xujiayao.discord_mc_chat.network.protocol;

/**
 * Every packet type DMCC can put on the wire.
 * <p>
 * The codec maps these names explicitly to record classes; there is no class-name based auto-discovery, so
 * a malformed or unknown type can never make the receiver load an arbitrary class.
 *
 * @author Xujiayao
 */
public enum PacketType {
	HANDSHAKE,
	CHALLENGE,
	AUTH_RESPONSE,
	LOGIN_SUCCESS,
	DISCONNECT,
	KEEP_ALIVE,
	LATENCY_PING,
	LATENCY_PONG,
	COMMAND_REQUEST,
	COMMAND_RESULT,
	COMMAND_FILE_CHUNK,
	AUTOCOMPLETE_REQUEST,
	AUTOCOMPLETE_RESULT,
	INFO_REQUEST,
	INFO_SNAPSHOT,
	LINK_REQUEST,
	LINK_RESULT,
	UNLINK_REQUEST,
	UNLINK_RESULT,
	OP_SYNC,
	DISCORD_RELAY,
	MINECRAFT_RELAY,
	MINECRAFT_EVENT,
	CONSOLE_LOG_BATCH
}
