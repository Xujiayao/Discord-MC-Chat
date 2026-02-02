package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Sent by server to client as an RTT response.
 *
 * @author Xujiayao
 */
public class LatencyPongPacket extends Packet {
	public long sentAtMillis;

	public LatencyPongPacket(long sentAtMillis) {
		this.sentAtMillis = sentAtMillis;
	}
}
