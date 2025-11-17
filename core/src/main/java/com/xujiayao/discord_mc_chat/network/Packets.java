package com.xujiayao.discord_mc_chat.network;

/**
 * Defines the network packets used for communication between the client and server.
 *
 * @author Xujiayao
 */
public class Packets {

	/**
	 * Packet sent by the client immediately after connection to identify itself and its version.
	 * Corresponds to step 1 of the handshake.
	 */
	public record ClientHello(
			String serverName,
			String version
	) implements Packet {
	}

	/**
	 * Packet sent by the server with a random challenge.
	 * Corresponds to step 2 of the handshake.
	 */
	public record ServerChallenge(
			String challenge
	) implements Packet {
	}

	/**
	 * Packet sent by the client with the calculated response hash.
	 * Corresponds to step 3 of the handshake.
	 */
	public record ClientResponse(
			String responseHash
	) implements Packet {
	}

	/**
	 * Packet sent by the server upon successful authentication.
	 * Carries the final message and the server's language setting.
	 * Corresponds to step 5 of the handshake.
	 */
	public record HandshakeSuccess(
			String messageKey,
			String language
	) implements Packet {
	}

	/**
	 * Packet sent by either side if the handshake fails at any stage.
	 */
	public record HandshakeFailure(
			String messageKey
	) implements Packet {
	}

	/**
	 * A packet sent periodically to keep the connection alive.
	 */
	public record Heartbeat() implements Packet {
	}
}
