package com.xujiayao.discord_mc_chat.network.packets.commands.link;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server to request a verification code for a Minecraft player.
 * <p>
 * When a player joins the server or runs {@code /dmcc link}, the Client sends this packet
 * so the Server can generate or refresh a verification code.
 *
 * @author Xujiayao
 */
public class LinkRequestPacket extends Packet {
	public String minecraftUuid;
	public String playerName;

	public LinkRequestPacket(String minecraftUuid, String playerName) {
		this.minecraftUuid = minecraftUuid;
		this.playerName = playerName;
	}
}
