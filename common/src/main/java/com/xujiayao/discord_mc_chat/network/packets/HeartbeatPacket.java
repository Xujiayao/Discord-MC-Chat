package com.xujiayao.discord_mc_chat.network.packets;

/**
 * A packet sent periodically to keep the connection alive.
 *
 * @author Xujiayao
 */
public record HeartbeatPacket() implements Packet {
}