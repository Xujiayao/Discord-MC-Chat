package com.xujiayao.discord_mc_chat.network.packets.commands.execute;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.List;

/**
 * Sent by Client to Server with auto-complete suggestions for a DMCC command.
 *
 * @author Xujiayao
 */
public class ExecuteAutoCompleteResponsePacket extends Packet {
	public String serverName;
	public List<String> suggestions;

	public ExecuteAutoCompleteResponsePacket(String serverName, List<String> suggestions) {
		this.serverName = serverName;
		this.suggestions = suggestions;
	}
}
