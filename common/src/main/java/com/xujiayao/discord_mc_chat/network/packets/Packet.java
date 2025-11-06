package com.xujiayao.discord_mc_chat.network.packets;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for all network packets.
 * Uses Jackson annotations for polymorphic deserialization.
 *
 * @author Xujiayao
 */
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = HeartbeatPacket.class, name = "heartbeat"),
		@JsonSubTypes.Type(value = PlayerChatPacket.class, name = "playerChat"),
		// Add other packet types here as they are created
		@JsonSubTypes.Type(value = DisplayMessagePacket.class, name = "displayMessage")
})
public interface Packet {
}
