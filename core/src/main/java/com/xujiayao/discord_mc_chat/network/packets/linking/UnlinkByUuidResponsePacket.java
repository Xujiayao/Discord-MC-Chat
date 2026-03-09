package com.xujiayao.discord_mc_chat.network.packets.linking;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client in response to an {@link UnlinkByUuidRequestPacket}.
 * <p>
 * Contains the result of the unlink operation.
 *
 * @author Xujiayao
 */
public class UnlinkByUuidResponsePacket extends Packet {
	public String minecraftUuid;
	public boolean success;

	public UnlinkByUuidResponsePacket(String minecraftUuid, boolean success) {
		this.minecraftUuid = minecraftUuid;
		this.success = success;
	}
}
