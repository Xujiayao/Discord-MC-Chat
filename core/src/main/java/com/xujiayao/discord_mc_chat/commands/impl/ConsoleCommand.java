package com.xujiayao.discord_mc_chat.commands.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.JdaCommandSender;
import com.xujiayao.discord_mc_chat.server.discord.OpLevelResolver;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Console command implementation.
 * Executes Minecraft commands on a Minecraft server.
 * <p>
 * In standalone mode: routes the command via Netty to a target client.
 * In single_server mode: executes the command directly on the local Minecraft server.
 * <p>
 * Authorization for Minecraft commands is handled by Minecraft's own permission system.
 * The sender's OP level is embedded in the virtual CommandSourceStack.
 *
 * @author Xujiayao
 */
public final class ConsoleCommand implements Command {

	private static final int CONSOLE_TIMEOUT_SECONDS = 30;
	private static final int LOCAL_COMMAND_TIMEOUT_SECONDS = 10;
	private static final Map<String, CompletableFuture<CommandPackets.Console.ResponsePacket>> pendingRequests = new ConcurrentHashMap<>();

	/**
	 * Completes a pending console request with the given response.
	 *
	 * @param requestId The request ID
	 * @param response  The response packet
	 */
	public static void completeRequest(String requestId, CommandPackets.Console.ResponsePacket response) {
		CompletableFuture<CommandPackets.Console.ResponsePacket> future = pendingRequests.remove(requestId);
		if (future != null && !future.isDone()) {
			future.complete(response);
		}
	}

	@Override
	public String name() {
		return "console";
	}

	@Override
	public CommandArgument[] args() {
		if ("standalone".equals(ModeManager.getMode())) {
			// standalone: /console <at> <command>
			return new CommandArgument[]{
					new CommandArgument() {
						@Override
						public String name() {
							return "at";
						}

						@Override
						public String description() {
							return I18nManager.getDmccTranslation("commands.console.args_desc.at");
						}
					},
					new CommandArgument() {
						@Override
						public String name() {
							return "command";
						}

						@Override
						public String description() {
							return I18nManager.getDmccTranslation("commands.console.args_desc.command");
						}
					}
			};
		} else {
			// single_server: /console <command>
			return new CommandArgument[]{
					new CommandArgument() {
						@Override
						public String name() {
							return "command";
						}

						@Override
						public String description() {
							return I18nManager.getDmccTranslation("commands.console.args_desc.command");
						}
					}
			};
		}
	}

	@Override
	public boolean acceptsExtraArgs() {
		// The "command" argument may contain spaces (e.g. "time set day")
		return true;
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.console.description");
	}

	@Override
	public boolean isVisibleFromMinecraft() {
		return false;
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if ("standalone".equals(ModeManager.getMode())) {
			executeStandalone(sender, args);
		} else {
			executeLocal(sender, args);
		}
	}

	/**
	 * Executes a Minecraft command on the local server (single_server mode).
	 * Dispatches via CoreEvents.MinecraftCommandExecutionEvent with callback-based completion.
	 *
	 * @param sender The command sender.
	 * @param args   args[0] = command (may contain spaces from acceptsExtraArgs).
	 */
	private void executeLocal(CommandSender sender, String[] args) {
		// Rebuild the full command line from all args
		String commandLine = String.join(" ", args);
		if (commandLine.startsWith("/")) {
			commandLine = commandLine.substring(1);
		}

		if (commandLine.startsWith("dmcc")) {
			sender.reply(I18nManager.getDmccTranslation("commands.console.dmcc_not_supported"));
			return;
		}

		sender.reply(I18nManager.getDmccTranslation("commands.console.executing_local", commandLine));

		CompletableFuture<Void> completionFuture = new CompletableFuture<>();

		EventManager.post(new CoreEvents.MinecraftCommandExecutionEvent(sender, commandLine, completionFuture));

		// Wait for the command to complete with a timeout
		try {
			completionFuture.get(LOCAL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			// Timeout or interruption - the command may still be running,
			// but we've already sent all output that was produced so far via the sender.
		}
	}

	/**
	 * Executes a Minecraft command on a remote client (standalone mode).
	 * Routes via Netty RequestPacket.
	 *
	 * @param sender The command sender.
	 * @param args   args[0] = at, args[1...] = command.
	 */
	private void executeStandalone(CommandSender sender, String[] args) {
		String target = args[0];
		// Rebuild the command from remaining args
		StringBuilder commandBuilder = new StringBuilder();
		for (int i = 1; i < args.length; i++) {
			if (i > 1) commandBuilder.append(" ");
			commandBuilder.append(args[i]);
		}
		String commandLine = commandBuilder.toString();
		if (commandLine.startsWith("/")) {
			commandLine = commandLine.substring(1);
		}

		if (commandLine.startsWith("dmcc")) {
			sender.reply(I18nManager.getDmccTranslation("commands.console.dmcc_not_supported"));
			return;
		}

		List<String> targets = new ArrayList<>();
		List<String> allConnected = NetworkManager.getConnectedClientNames();
		String targetName;

		if ("all_online_clients".equalsIgnoreCase(target)) {
			if (allConnected.isEmpty()) {
				sender.reply(I18nManager.getDmccTranslation("commands.console.no_online_clients"));
				return;
			}
			targets.addAll(allConnected);
			targetName = I18nManager.getDmccTranslation("commands.console.all_online_clients");
		} else {
			if (!isValidTarget(target)) {
				sender.reply(I18nManager.getDmccTranslation("commands.console.invalid_target", target, allConnected));
				return;
			}
			if (!allConnected.contains(target)) {
				sender.reply(I18nManager.getDmccTranslation("commands.console.client_offline", target));
				return;
			}
			targets.add(target);
			targetName = target;
		}

		sender.reply(I18nManager.getDmccTranslation("commands.console.executing", commandLine, targetName));

		for (String serverName : targets) {
			if (!NetworkManager.isClientConnected(serverName)) {
				if (sender instanceof JdaCommandSender) {
					DiscordManager.sendExecuteResultViaWebhook(serverName,
							I18nManager.getDmccTranslation("commands.console.client_offline", serverName));
				} else {
					sender.reply(I18nManager.getDmccTranslation("commands.console.client_offline", serverName));
				}
				continue;
			}

			String requestId = CryptUtils.generateRandomString(16);
			CompletableFuture<CommandPackets.Console.ResponsePacket> future = new CompletableFuture<>();
			pendingRequests.put(requestId, future);

			// Resolve per-server OP level for the target client
			int opLevel = sender.getOpLevel();
			if (sender instanceof JdaCommandSender jdaSender) {
				opLevel = OpLevelResolver.resolveForServer(jdaSender.getMember(), jdaSender.getUser(), serverName);
			}

			// Send with the sender's per-server OP level for Minecraft's own permission check on the client
			NetworkManager.sendPacketToClient(new CommandPackets.Console.RequestPacket(requestId, opLevel, commandLine), serverName);

			try {
				CommandPackets.Console.ResponsePacket response = future.get(CONSOLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				String output = response.response;

				if (sender instanceof JdaCommandSender) {
					if (output.isBlank()) {
						String command = "/execute at: " + serverName + " command: log latest.log";
						output = I18nManager.getDmccTranslation("commands.console.no_response", command);
					}

					DiscordManager.sendExecuteResultViaWebhook(serverName, output);
				} else {
					if (output.isBlank()) {
						String command = "/execute " + serverName + " log latest.log";
						output = I18nManager.getDmccTranslation("commands.console.no_response", command);
					}

					String[] lines = output.split("\n");
					for (String line : lines) {
						sender.reply(I18nManager.getDmccTranslation("commands.remote_result_prefix", serverName, line));
					}
				}
			} catch (Exception e) {
				String timeoutMsg = I18nManager.getDmccTranslation("commands.console.timeout", serverName);
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

	/**
	 * Checks if the target server name is valid according to the configuration.
	 *
	 * @param target The target server name.
	 * @return true if valid, false otherwise.
	 */
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

