package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by server to challenge the client.
 *
 * @author Xujiayao
 */
public final class ChallengePacket extends Packet {
	public String salt;

	public ChallengePacket(String salt) {
		this.salt = salt;
	}
}
