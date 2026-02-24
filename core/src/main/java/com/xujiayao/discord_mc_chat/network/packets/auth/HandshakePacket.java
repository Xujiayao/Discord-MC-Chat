package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by client to initiate connection.
 *
 * @author Xujiayao
 */
public class HandshakePacket extends Packet {
	public String serverName;
	public String dmccVersion;
	public String minecraftVersion;

	public HandshakePacket(String serverName, String dmccVersion, String minecraftVersion) {
		this.serverName = serverName;
		this.dmccVersion = dmccVersion;
		this.minecraftVersion = minecraftVersion;
	}
}
