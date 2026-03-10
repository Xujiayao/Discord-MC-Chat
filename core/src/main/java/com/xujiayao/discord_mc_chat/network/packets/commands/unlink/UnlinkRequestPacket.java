package com.xujiayao.discord_mc_chat.network.packets.commands.unlink;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server to unlink a Minecraft player by UUID.
 * <p>
 * Used when a Minecraft player runs {@code /dmcc unlink} in multi-server mode.
 *
 * @author Xujiayao
 */
public class UnlinkRequestPacket extends Packet {
	public String minecraftUuid;
	public String playerName;

	public UnlinkRequestPacket(String minecraftUuid, String playerName) {
		this.minecraftUuid = minecraftUuid;
		this.playerName = playerName;
	}
}
