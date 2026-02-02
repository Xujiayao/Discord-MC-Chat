package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Sent by client to server to measure RTT latency.
 *
 * @author Xujiayao
 */
public class LatencyPingPacket extends Packet {
	public long sentAtMillis;

	public LatencyPingPacket(long sentAtMillis) {
		this.sentAtMillis = sentAtMillis;
	}
}
