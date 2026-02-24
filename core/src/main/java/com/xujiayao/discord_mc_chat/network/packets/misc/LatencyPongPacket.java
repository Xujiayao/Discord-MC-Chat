package com.xujiayao.discord_mc_chat.network.packets.misc;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

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
