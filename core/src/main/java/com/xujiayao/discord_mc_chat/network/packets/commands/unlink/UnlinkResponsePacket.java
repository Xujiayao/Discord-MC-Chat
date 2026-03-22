package com.xujiayao.discord_mc_chat.network.packets.commands.unlink;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client in response to an {@link UnlinkRequestPacket}.
 * <p>
 * Contains the result of the unlink operation.
 *
 * @author Xujiayao
 */
public final class UnlinkResponsePacket extends Packet {
	public String minecraftUuid;
	public boolean success;
	public String discordName;

	public UnlinkResponsePacket(String minecraftUuid, boolean success, String discordName) {
		this.minecraftUuid = minecraftUuid;
		this.success = success;
		this.discordName = discordName;
	}
}
