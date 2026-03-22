package com.xujiayao.discord_mc_chat.network.packets.commands.console;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client to request auto-complete suggestions for a Minecraft command.
 *
 * @author Xujiayao
 */
public final class ConsoleAutoCompleteRequestPacket extends Packet {
	public String input;
	public int opLevel;

	public ConsoleAutoCompleteRequestPacket(String input, int opLevel) {
		this.input = input;
		this.opLevel = opLevel;
	}
}
