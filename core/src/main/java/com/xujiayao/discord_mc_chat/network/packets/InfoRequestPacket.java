package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Sent by Server to Client to request status information.
 *
 * @author Xujiayao
 */
public class InfoRequestPacket extends Packet {
	public long sentAtMillis;

	public InfoRequestPacket(long sentAtMillis) {
		this.sentAtMillis = sentAtMillis;
	}
}
