package com.xujiayao.discord_mc_chat.network.packets.commands;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server with the result of a Minecraft command execution.
 *
 * @author Xujiayao
 */
public class ConsoleResponsePacket extends Packet {
	public String requestId;
	public String response;

	public ConsoleResponsePacket(String requestId, String response) {
		this.requestId = requestId;
		this.response = response;
	}
}
