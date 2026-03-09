package com.xujiayao.discord_mc_chat.network.packets.linking;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server to unlink a Minecraft player by UUID.
 * <p>
 * Used when a Minecraft player runs {@code /dmcc unlink} in multi-server mode.
 *
 * @author Xujiayao
 */
public class UnlinkByUuidRequestPacket extends Packet {
	public String minecraftUuid;
	public String playerName;

	public UnlinkByUuidRequestPacket(String minecraftUuid, String playerName) {
		this.minecraftUuid = minecraftUuid;
		this.playerName = playerName;
	}
}
