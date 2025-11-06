package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Packet for sending a player's chat message from client to server.
 *
 * @author Xujiayao
 */
public record PlayerChatPacket(String serverName, String playerName, String message) implements Packet {
}
