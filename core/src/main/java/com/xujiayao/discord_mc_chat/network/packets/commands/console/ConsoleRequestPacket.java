package com.xujiayao.discord_mc_chat.network.packets.commands.console;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client to request execution of a Minecraft command.
 * <p>
 * This packet carries the sender's OP level credential.
 * The Client will construct a virtual CommandSourceStack with this OP level
 * and dispatch the command to the Minecraft command dispatcher.
 * Minecraft's own permission system will handle authorization.
 *
 * @author Xujiayao
 */
public final class ConsoleRequestPacket extends Packet {
	public String requestId;
	public int opLevel;
	public String commandLine;

	public ConsoleRequestPacket(String requestId, int opLevel, String commandLine) {
		this.requestId = requestId;
		this.opLevel = opLevel;
		this.commandLine = commandLine;
	}
}
