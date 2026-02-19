package com.xujiayao.discord_mc_chat.commands.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.ExecuteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.ExecuteResponsePacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.JdaCommandSender;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Execute command implementation (standalone only).
 * Forwards DMCC commands to connected clients.
 * Results are sent via each client's webhook in the Discord channel.
 *
 * @author Xujiayao
 */
public class ExecuteCommand implements Command {

	private static final int EXECUTE_TIMEOUT_SECONDS = 30;
	private static final Map<String, CompletableFuture<ExecuteResponsePacket>> pendingRequests = new ConcurrentHashMap<>();

	/**
	 * Completes a pending execute request with the given response.
	 *
	 * @param requestId The request ID
	 * @param response  The response packet
	 */
	public static void completeRequest(String requestId, ExecuteResponsePacket response) {
		CompletableFuture<ExecuteResponsePacket> future = pendingRequests.remove(requestId);
		if (future != null && !future.isDone()) {
			future.complete(response);
		}
	}

	@Override
	public String name() {
		return "execute";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[]{
				new CommandArgument() {
					@Override
					public String name() {
						return "at";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.execute.args_desc.at");
					}
				},
				new CommandArgument() {
					@Override
					public String name() {
						return "command";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.execute.args_desc.command");
					}
				}
		};
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.execute.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if (args.length < 2) {
			sender.reply(I18nManager.getDmccTranslation("commands.execute.usage"));
			return;
		}

		String target = args[0];
		String command = args[1];

		// Parse the command and its arguments from the single command string
		String[] commandParts = command.trim().split("\\s+");
		String commandName = commandParts[0].toLowerCase();
		if (commandName.startsWith("/")) {
			commandName = commandName.substring(1);
		}
		String[] commandArgs = commandParts.length > 1
				? command.substring(command.indexOf(' ') + 1).split("\\s+")
				: new String[0];

		List<String> targets = new ArrayList<>();
		List<String> allConnected = NetworkManager.getConnectedClientNames();
		if ("all_connected".equalsIgnoreCase(target)) {
			if (allConnected.isEmpty()) {
				sender.reply(I18nManager.getDmccTranslation("commands.execute.no_online_clients"));
				return;
			}
			targets.addAll(allConnected);
		} else {
			if (!isValidTarget(target)) {
				sender.reply(I18nManager.getDmccTranslation("commands.execute.invalid_target", target));
				return;
			}
			if (!allConnected.contains(target)) {
				sender.reply(I18nManager.getDmccTranslation("commands.execute.client_offline", target));
				return;
			}
			targets.add(target);
		}

		// Inform the sender that execution is in progress
		sender.reply(I18nManager.getDmccTranslation("commands.execute.executing", command, target));

		for (String serverName : targets) {
			if (!NetworkManager.isClientConnected(serverName)) {
				// Offline status is sent via webhook for each server, or directly to sender for terminal
				if (sender instanceof JdaCommandSender) {
					DiscordManager.sendExecuteResultViaWebhook(serverName,
							I18nManager.getDmccTranslation("commands.execute.client_offline", serverName));
				} else {
					sender.reply(I18nManager.getDmccTranslation("commands.execute.client_offline", serverName));
				}
				continue;
			}

			String requestId = CryptUtils.generateRandomString(16);
			CompletableFuture<ExecuteResponsePacket> future = new CompletableFuture<>();
			pendingRequests.put(requestId, future);

			NetworkManager.sendPacketToClient(new ExecuteRequestPacket(requestId, commandName, commandArgs), serverName);

			try {
				ExecuteResponsePacket response = future.get(EXECUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

				if (sender instanceof JdaCommandSender) {
					// Discord: send result via webhook with server name
					if (response.fileData != null && response.fileName != null) {
						DiscordManager.sendExecuteResultWithFileViaWebhook(serverName,
								response.response, response.fileData, response.fileName);
					} else {
						DiscordManager.sendExecuteResultViaWebhook(serverName, response.response);
					}
				} else {
					// Terminal: send result directly
					if (response.fileData != null && response.fileName != null) {
						sender.replyWithFile(response.response, response.fileData, response.fileName);
					} else {
						String[] lines = response.response.split("\n");
						for (String line : lines) {
							sender.reply("[" + serverName + "] " + line);
						}
					}
				}
			} catch (Exception e) {
				String timeoutMsg = I18nManager.getDmccTranslation("commands.execute.timeout", serverName);
				if (sender instanceof JdaCommandSender) {
					DiscordManager.sendExecuteResultViaWebhook(serverName, timeoutMsg);
				} else {
					sender.reply(timeoutMsg);
				}
			} finally {
				pendingRequests.remove(requestId);
			}
		}
	}

	private boolean isValidTarget(String target) {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (target.equals(node.path("name").asText())) {
					return true;
				}
			}
		}
		return false;
	}
}
