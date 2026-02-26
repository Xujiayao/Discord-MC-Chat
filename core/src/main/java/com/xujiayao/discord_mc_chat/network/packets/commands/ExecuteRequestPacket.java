package com.xujiayao.discord_mc_chat.network.packets.commands;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Server to Client to request execution of a DMCC command.
 * <p>
 * This packet carries the sender's OP level credential for edge authorization.
 * The Client will compare this credential against its local permission configuration
 * before executing the command.
 *
 * @author Xujiayao
 */
public class ExecuteRequestPacket extends Packet {
	public String requestId;
	public int opLevel;
	public String command;
	public String[] args;

	public ExecuteRequestPacket(String requestId, int opLevel, String command, String... args) {
		this.requestId = requestId;
		this.opLevel = opLevel;
		this.command = command;
		this.args = args;
	}
}
