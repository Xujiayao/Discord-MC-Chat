package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by client with the calculated hash.
 *
 * @author Xujiayao
 */
public final class AuthResponsePacket extends Packet {
	public String hash;

	public AuthResponsePacket(String hash) {
		this.hash = hash;
	}
}
