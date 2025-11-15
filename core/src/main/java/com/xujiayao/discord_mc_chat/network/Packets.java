package com.xujiayao.discord_mc_chat.network;

/**
 * Defines the network packets used for communication between the client and server.
 *
 * @author Xujiayao
 */
public class Packets {

	/**
	 * Packet sent by the client immediately after connection to identify itself.
	 */
	public record Handshake(String serverName) implements Packet {
	}

	/**
	 * Packet sent by the server in response to a Handshake packet.
	 */
	public record HandshakeResponse(boolean success, String message) implements Packet {
	}

	/**
	 * A packet sent periodically to keep the connection alive.
	 */
	public record Heartbeat() implements Packet {
	}
}
