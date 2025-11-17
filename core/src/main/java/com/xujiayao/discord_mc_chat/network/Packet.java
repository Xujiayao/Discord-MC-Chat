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
		@JsonSubTypes.Type(value = Packets.ClientHello.class, name = "clientHello"),
		@JsonSubTypes.Type(value = Packets.ServerChallenge.class, name = "serverChallenge"),
		@JsonSubTypes.Type(value = Packets.ClientResponse.class, name = "clientResponse"),
		@JsonSubTypes.Type(value = Packets.HandshakeSuccess.class, name = "handshakeSuccess"),
		@JsonSubTypes.Type(value = Packets.HandshakeFailure.class, name = "handshakeFailure"),
		@JsonSubTypes.Type(value = Packets.Heartbeat.class, name = "heartbeat")
})
public interface Packet {
}
