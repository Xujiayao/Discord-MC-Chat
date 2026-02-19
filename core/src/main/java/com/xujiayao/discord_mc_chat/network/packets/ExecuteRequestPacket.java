package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Sent by Server to Client to request execution of a DMCC command.
 *
 * @author Xujiayao
 */
public class ExecuteRequestPacket extends Packet {
	public String requestId;
	public String command;
	public String[] args;

	public ExecuteRequestPacket(String requestId, String command, String... args) {
		this.requestId = requestId;
		this.command = command;
		this.args = args;
	}
}
