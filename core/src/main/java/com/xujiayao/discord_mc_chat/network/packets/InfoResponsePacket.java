package com.xujiayao.discord_mc_chat.network.packets;

import java.util.Map;

/**
 * Sent by Client to Server containing status information.
 *
 * @author Xujiayao
 */
public class InfoResponsePacket extends Packet {
	public String serverName;

	public int onlinePlayerCount;
	public int maxPlayerCount;
	public Map<String, Integer> playersAndLatencies;

	public double tps;
	public double mspt;

	public long uptimeSeconds;

	public long totalMemory;
	public long freeMemory;

	public InfoResponsePacket(String serverName, int onlinePlayerCount, int maxPlayerCount,
							  Map<String, Integer> playersAndLatencies, double tps, double mspt,
							  long uptimeSeconds, long totalMemory, long freeMemory) {
		this.serverName = serverName;
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
