package com.xujiayao.discord_mc_chat.network.packets.commands.console;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.List;

/**
 * Sent by Client to Server with auto-complete suggestions for a Minecraft command.
 *
 * @author Xujiayao
 */
public final class ConsoleAutoCompleteResponsePacket extends Packet {
	public String serverName;
	public List<String> suggestions;

	public ConsoleAutoCompleteResponsePacket(String serverName, List<String> suggestions) {
		this.serverName = serverName;
		this.suggestions = suggestions;
	}
}
