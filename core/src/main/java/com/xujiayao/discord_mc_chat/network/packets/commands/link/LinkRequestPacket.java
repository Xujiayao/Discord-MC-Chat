package com.xujiayao.discord_mc_chat.network.packets.commands.link;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server to request a verification code for a Minecraft player.
 * <p>
 * When a player joins the server or runs {@code /dmcc link}, the Client sends this packet
 * so the Server can generate or refresh a verification code.
 * <p>
 * When {@code joinCheck} is true, the Server should only respond if the player is NOT linked
 * (i.e., generate a code and send a response). If the player is already linked, no response is sent.
 *
 * @author Xujiayao
 */
public final class LinkRequestPacket extends Packet {
	public String minecraftUuid;
	public String playerName;
	public boolean joinCheck;

	public LinkRequestPacket(String minecraftUuid, String playerName, boolean joinCheck) {
		this.minecraftUuid = minecraftUuid;
		this.playerName = playerName;
		this.joinCheck = joinCheck;
	}
}
