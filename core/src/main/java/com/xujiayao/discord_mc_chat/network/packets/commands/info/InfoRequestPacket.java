package com.xujiayao.discord_mc_chat.network.packets.commands.info;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client to request status information.
 *
 * @author Xujiayao
 */
public final class InfoRequestPacket extends Packet {
	public long sentAtMillis;

	public InfoRequestPacket(long sentAtMillis) {
		this.sentAtMillis = sentAtMillis;
	}
}
