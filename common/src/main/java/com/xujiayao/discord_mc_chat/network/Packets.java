package com.xujiayao.discord_mc_chat.network;

/**
 * Defines the network packets used for communication between the client and server.
 *
 * @author Xujiayao
 */
public class Packets {

	/**
	 * Packet for sending a message from server to client to be displayed in-game.
	 */
	public record DisplayMessage(String jsonMessage) implements Packet {
	}

	/**
	 * A packet sent periodically to keep the connection alive.
	 */
	public record Heartbeat() implements Packet {
	}

	/**
	 * Packet for sending a player's chat message from client to server.
	 */
	public record PlayerChat(String serverName, String playerName, String message) implements Packet {
	}
}
