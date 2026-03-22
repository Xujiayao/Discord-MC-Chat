package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by either side to disconnect with a localized reason.
 *
 * @author Xujiayao
 */
public final class DisconnectPacket extends Packet {
	public String key;
	public Object[] args;

	public DisconnectPacket(String key, Object... args) {
		this.key = key;
		this.args = args;
	}
}
