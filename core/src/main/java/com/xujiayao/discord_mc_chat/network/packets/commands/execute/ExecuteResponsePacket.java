package com.xujiayao.discord_mc_chat.network.packets.commands.execute;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by Client to Server with the result of a command execution.
 *
 * @author Xujiayao
 */
public class ExecuteResponsePacket extends Packet {
	public String requestId;
	public String response;
	public byte[] fileData;
	public String fileName;

	public ExecuteResponsePacket(String requestId, String response) {
		this.requestId = requestId;
		this.response = response;
		this.fileData = null;
		this.fileName = null;
	}

	public ExecuteResponsePacket(String requestId, String response, byte[] fileData, String fileName) {
		this.requestId = requestId;
		this.response = response;
		this.fileData = fileData;
		this.fileName = fileName;
	}
}
