package com.xujiayao.discord_mc_chat.network.packets.commands;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client to request auto-complete suggestions for a DMCC command.
 *
 * @author Xujiayao
 */
public class CommandAutoCompleteRequestPacket extends Packet {
	public String input;

	public CommandAutoCompleteRequestPacket(String input) {
		this.input = input;
	}
}
