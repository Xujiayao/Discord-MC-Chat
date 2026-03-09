package com.xujiayao.discord_mc_chat.network.packets.linking;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client in response to a {@link LinkCodeRequestPacket}.
 * <p>
 * Contains the generated verification code for the Minecraft player,
 * or indicates that the player is already linked.
 *
 * @author Xujiayao
 */
public class LinkCodeResponsePacket extends Packet {
	public String minecraftUuid;
	public String code;
	public boolean alreadyLinked;

	public LinkCodeResponsePacket(String minecraftUuid, String code, boolean alreadyLinked) {
		this.minecraftUuid = minecraftUuid;
		this.code = code;
		this.alreadyLinked = alreadyLinked;
	}
}
