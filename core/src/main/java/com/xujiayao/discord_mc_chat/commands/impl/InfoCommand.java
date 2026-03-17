package com.xujiayao.discord_mc_chat.commands.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager.DiscordStatusInfo;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Info command implementation.
 *
 * @author Xujiayao
 */
public class InfoCommand implements Command {

	private static final int INFO_REQUEST_TIMEOUT_SECONDS = 3;

	private static String buildServerPart(Map<String, InfoResponsePacket> infoSnapshot) {
		int onlineClients = infoSnapshot.size();
		int totalClients = getConfiguredClientCount();
		if (totalClients == 0) {
			totalClients = onlineClients;
		}

		String clientsInfo;
		if (infoSnapshot.isEmpty()) {
			clientsInfo = "  " + I18nManager.getDmccTranslation("commands.info.server_part.no_clients");
		} else {
			StringBuilder clientsBuilder = new StringBuilder();
			infoSnapshot.values().stream()
					.sorted(Comparator.comparing(packet -> packet.serverName == null ? "" : packet.serverName, String.CASE_INSENSITIVE_ORDER))
					.forEach(packet -> {
						clientsBuilder.append("  ");
						clientsBuilder.append(buildServerClientInfo(packet));
					});
			clientsInfo = clientsBuilder.toString();
		}

		return I18nManager.getDmccTranslation("commands.info.server_part.base", onlineClients, totalClients, clientsInfo);
	}

	private static String buildServerClientInfo(InfoResponsePacket packet) {
		String playersInfo = buildPlayersInfo(packet);
		long[] uptime = splitSeconds(packet.uptimeSeconds);
		long usedMemoryMiB = toMiB(packet.totalMemory - packet.freeMemory);
		long totalMemoryMiB = toMiB(packet.totalMemory);

		return I18nManager.getDmccTranslation("commands.info.server_part.clients",
				packet.serverName,
				packet.connectionLatencyMillis,
				packet.minecraftVersion,
				packet.onlinePlayerCount,
				packet.maxPlayerCount,
				playersInfo,
				String.format("%.2f", packet.tps),
				String.format("%.2f", packet.mspt),
				uptime[0], uptime[1], uptime[2], uptime[3],
				usedMemoryMiB, totalMemoryMiB);
	}

	private static String buildClientPart(Map<String, InfoResponsePacket> infoSnapshot, long latencyOverride, boolean clientConnected) {
		InfoResponsePacket packet = getClientPacket(infoSnapshot);

		String connectionStatus = "DISCONNECTED";
		long connectionLatency = -1;

		if (clientConnected) {
			connectionStatus = "CONNECTED";
			connectionLatency = latencyOverride >= 0 ? latencyOverride : packet.connectionLatencyMillis;
			if (connectionLatency < 0) {
				var client = NetworkManager.getClient();
				connectionLatency = client == null ? -1 : client.getConnectionLatencyMillis();
			}
		}

		String playersInfo = buildPlayersInfo(packet);

		return I18nManager.getDmccTranslation("commands.info.client_part",
				connectionStatus,
				connectionLatency,
				packet.minecraftVersion,
				packet.onlinePlayerCount,
				packet.maxPlayerCount,
				playersInfo,
				String.format("%.2f", packet.tps),
				String.format("%.2f", packet.mspt));
	}

	private static String buildPlayersInfo(InfoResponsePacket packet) {
		if (packet.playersAndLatencies == null || packet.playersAndLatencies.isEmpty()) {
			return I18nManager.getDmccTranslation("commands.info.no_players");
		}

		StringBuilder playersBuilder = new StringBuilder();
		packet.playersAndLatencies.forEach((name, latency) -> {
			if (!playersBuilder.isEmpty()) {
				playersBuilder.append("\n");
			}
			playersBuilder.append(I18nManager.getDmccTranslation("commands.info.players", latency, name));
		});

		return playersBuilder.toString();
	}

	private static InfoResponsePacket getClientPacket(Map<String, InfoResponsePacket> infoSnapshot) {
		String serverName = NetworkManager.getClientServerName();
		InfoResponsePacket packet = infoSnapshot.get(serverName);

		if (packet == null && !infoSnapshot.isEmpty()) {
			packet = infoSnapshot.values().iterator().next();
		}

		if (packet == null) {
			packet = NetworkManager.createInfoResponsePacket();
		}

		return packet;
	}

	private static int getConfiguredClientCount() {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode != null && serversNode.isArray()) {
			return serversNode.size();
		}
		return 0;
	}

	private static long[] splitSeconds(long totalSeconds) {
		long days = totalSeconds / 86400;
		long hours = (totalSeconds % 86400) / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		return new long[]{days, hours, minutes, seconds};
	}

	private static long toMiB(long bytes) {
		return bytes / (1024L * 1024L);
	}

	@Override
	public String name() {
		return "info";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.info.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		CompletableFuture<Map<String, InfoResponsePacket>> infoFuture =
				CompletableFuture.supplyAsync(() -> NetworkManager.requestInfoSnapshot(INFO_REQUEST_TIMEOUT_SECONDS));

		CompletableFuture<DiscordStatusInfo> discordFuture = null;
		if (!"multi_server_client".equals(ModeManager.getMode())) {
			discordFuture = CompletableFuture.supplyAsync(DiscordManager::getStatusInfo);
		}

		ClientDMCC client = NetworkManager.getClient();
		boolean clientConnected = client != null && client.isConnected();

		CompletableFuture<Long> latencyFuture = null;
		if (clientConnected) {
			long timeoutMillis = TimeUnit.SECONDS.toMillis(INFO_REQUEST_TIMEOUT_SECONDS);
			latencyFuture = CompletableFuture.supplyAsync(() -> client.requestLatencySample(timeoutMillis));
		}

		Map<String, InfoResponsePacket> infoSnapshot = infoFuture.join();
		DiscordStatusInfo statusInfo = discordFuture == null ? null : discordFuture.join();
		long latencyOverride = latencyFuture == null ? -1 : latencyFuture.join();

		StringBuilder builder = new StringBuilder();

		// Common part
		long uptimeSeconds = TimeUnit.MILLISECONDS.toSeconds(ManagementFactory.getRuntimeMXBean().getUptime());
		long[] uptime = splitSeconds(uptimeSeconds);

		long totalMemoryMiB = toMiB(Runtime.getRuntime().totalMemory());
		long usedMemoryMiB = toMiB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());

		builder.append(I18nManager.getDmccTranslation("commands.info.common_part",
				Constants.VERSION,
				ModeManager.getMode(),
				uptime[0], uptime[1], uptime[2], uptime[3],
				usedMemoryMiB, totalMemoryMiB));

		// Discord part
		if (statusInfo != null) {
			builder.append("\n");
			builder.append(I18nManager.getDmccTranslation("commands.info.discord_part",
					statusInfo.status(),
					statusInfo.tag(),
					statusInfo.gatewayPingMillis(),
					statusInfo.restPingMillis()));
		}

		// Server/client part
		builder.append("\n");
		if (ModeManager.getMode().equals("standalone")) {
			builder.append(buildServerPart(infoSnapshot));
		} else {
			builder.append(buildClientPart(infoSnapshot, latencyOverride, clientConnected));
		}

		sender.reply(builder.toString());
	}
}
