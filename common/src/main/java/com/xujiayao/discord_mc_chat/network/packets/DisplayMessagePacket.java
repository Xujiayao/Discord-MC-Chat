package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Packet for sending a message from server to client to be displayed in-game.
 *
 * @author Xujiayao
 */
public record DisplayMessagePacket(String jsonMessage) implements Packet {
}
