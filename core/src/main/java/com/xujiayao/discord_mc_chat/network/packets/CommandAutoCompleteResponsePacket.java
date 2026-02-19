package com.xujiayao.discord_mc_chat.network.packets;

import java.util.List;

/**
 * Sent by Client to Server with auto-complete suggestions for a DMCC command.
 *
 * @author Xujiayao
 */
public class CommandAutoCompleteResponsePacket extends Packet {
	public String serverName;
	public List<String> suggestions;

	public CommandAutoCompleteResponsePacket(String serverName, List<String> suggestions) {
		this.serverName = serverName;
		this.suggestions = suggestions;
	}
}
