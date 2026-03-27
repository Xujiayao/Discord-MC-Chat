package com.xujiayao.discord_mc_chat.network.packets.commands.info;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.Map;

/**
 * Sent by Client to Server containing status information.
 *
 * @author Xujiayao
 */
public final class InfoResponsePacket extends Packet {
	public String serverName;

	public long connectionLatencyMillis;

	public String minecraftVersion;

	public int onlinePlayerCount;
	public int maxPlayerCount;
	public Map<String, Integer> playersAndLatencies;

	public double tps;
	public double mspt;

	public long uptimeSeconds;

	public long totalMemory;
	public long freeMemory;

	public InfoResponsePacket(String serverName, long connectionLatencyMillis, String minecraftVersion, int onlinePlayerCount,
	                          int maxPlayerCount, Map<String, Integer> playersAndLatencies, double tps, double mspt,
	                          long uptimeSeconds, long totalMemory, long freeMemory) {
		this.serverName = serverName;
		this.connectionLatencyMillis = connectionLatencyMillis;
		this.minecraftVersion = minecraftVersion;
		this.onlinePlayerCount = onlinePlayerCount;
		this.maxPlayerCount = maxPlayerCount;
		this.playersAndLatencies = playersAndLatencies;
		this.tps = tps;
		this.mspt = mspt;
		this.uptimeSeconds = uptimeSeconds;
		this.totalMemory = totalMemory;
		this.freeMemory = freeMemory;
	}
}
