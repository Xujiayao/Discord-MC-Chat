package com.xujiayao.discord_mc_chat.network;

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
		@JsonSubTypes.Type(value = Packets.Handshake.class, name = "handshake"),
		@JsonSubTypes.Type(value = Packets.HandshakeResponse.class, name = "handshakeResponse"),
		@JsonSubTypes.Type(value = Packets.Heartbeat.class, name = "heartbeat")
})
public interface Packet {
}
