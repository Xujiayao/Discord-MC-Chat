package com.xujiayao.discord_mc_chat.network.packets.commands.link;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.Map;

/**
 * Sent by Server to Client to synchronize OP levels for linked Minecraft accounts.
 * <p>
 * This packet contains a full mapping of Minecraft UUID to desired OP level.
 * The client should apply a full reset: de-op all players, then re-apply these levels.
 *
 * @author Xujiayao
 */
public class OpSyncPacket extends Packet {
	public Map<String, Integer> opLevels;

	public OpSyncPacket(Map<String, Integer> opLevels) {
		this.opLevels = opLevels;
	}
}
