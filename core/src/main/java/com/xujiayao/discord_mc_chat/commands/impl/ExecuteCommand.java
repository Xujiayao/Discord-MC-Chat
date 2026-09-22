package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.JdaCommandSender;
import com.xujiayao.discord_mc_chat.server.discord.OpLevelResolver;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;

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
public final class ExecuteCommand implements Command {

	private static final int EXECUTE_TIMEOUT_SECONDS = 30;
	private static final Map<String, CompletableFuture<CommandPackets.Execute.ResponsePacket>> pendingRequests = new ConcurrentHashMap<>();

	/**
	 * Completes a pending execute request with the given response.
	 *
	 * @param requestId The request ID
	 * @param response  The response packet
	 */
	public static void completeRequest(String requestId, CommandPackets.Execute.ResponsePacket response) {
		CompletableFuture<CommandPackets.Execute.ResponsePacket> future = pendingRequests.remove(requestId);
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
				new CommandArgument("at", I18nManager.getDmccTranslation("commands.execute.args_desc.at")),
				new CommandArgument("command", I18nManager.getDmccTranslation("commands.execute.args_desc.command"))
		};
	}

	@Override
	public boolean acceptsExtraArgs() {
		// The "command" argument from terminal parsing may contain extra tokens
		// that were not split because TerminalManager treats everything after <at> as one arg.
		// However, from Discord slash commands, args are always exactly [at, command].
		// We return true as a safety measure in case of edge cases.
		return true;
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.execute.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		String target = args[0];
		String command = args[1];

		// Split the trimmed command string once: deriving the name and the arguments from different
		// views of the input (trimmed vs. original) duplicated the first argument when it was
		// preceded by leading whitespace or separated by a tab.
		String[] commandHead = command.trim().split("\\s+", 2);
		String commandName = commandHead[0].toLowerCase();
		if (commandName.startsWith("/")) {
			commandName = commandName.substring(1);
		}
		String[] commandArgs = commandHead.length > 1 ? commandHead[1].split("\\s+") : new String[0];

		CommandManager.ResolvedTarget resolved = CommandManager.resolveTarget(sender, target, "commands.execute");
		if (resolved == null) {
			return;
		}

		// Inform the sender that execution is in progress
		sender.reply(I18nManager.getDmccTranslation("commands.execute.executing", command, resolved.displayName()));

		for (String serverName : resolved.clientNames()) {
			String discordChannelId = sender instanceof JdaCommandSender jdaSender ? jdaSender.getChannelId() : null;

			if (!NetworkManager.isClientConnected(serverName)) {
				// Offline status is sent via webhook for each server, or directly to sender for terminal
				if (sender instanceof JdaCommandSender) {
					DiscordManager.sendExecuteResultViaWebhook(discordChannelId, serverName,
							I18nManager.getDmccTranslation("commands.execute.client_offline", serverName));
				} else {
					sender.reply(I18nManager.getDmccTranslation("commands.execute.client_offline", serverName));
				}
				continue;
			}

			// Resolve per-server OP level for the target client
			int opLevel = sender.getOpLevel();
			if (sender instanceof JdaCommandSender jdaSender) {
				opLevel = OpLevelResolver.resolveForServer(jdaSender.getMember(), jdaSender.getUser(), serverName);
			}

			String requestId = CryptUtils.generateRandomString(16);
			CompletableFuture<CommandPackets.Execute.ResponsePacket> future = new CompletableFuture<>();
			pendingRequests.put(requestId, future);

			// Append sender's per-server OP level credential to the packet for client-side edge authorization
			NetworkManager.sendPacketToClient(new CommandPackets.Execute.RequestPacket(requestId, opLevel, commandName, commandArgs), serverName);

			try {
				CommandPackets.Execute.ResponsePacket response = future.get(EXECUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

				if (sender instanceof JdaCommandSender) {
					// Discord: send result via webhook with server name
					if (response.fileData != null && response.fileName != null) {
						DiscordManager.sendExecuteResultWithFileViaWebhook(discordChannelId, serverName,
								response.response, response.fileData, response.fileName);
					} else {
						DiscordManager.sendExecuteResultViaWebhook(discordChannelId, serverName, response.response);
					}
				} else {
					// Terminal: send result directly
					if (response.fileData != null && response.fileName != null) {
						sender.replyWithFile(I18nManager.getDmccTranslation("commands.remote_result_prefix", serverName, response.response), response.fileData, response.fileName);
					} else {
						String[] lines = response.response.split("\n");
						for (String line : lines) {
							sender.reply(I18nManager.getDmccTranslation("commands.remote_result_prefix", serverName, line));
						}
					}
				}
			} catch (Exception e) {
				String timeoutMsg = I18nManager.getDmccTranslation("commands.execute.timeout", serverName);
				if (sender instanceof JdaCommandSender) {
					DiscordManager.sendExecuteResultViaWebhook(discordChannelId, serverName, timeoutMsg);
				} else {
					sender.reply(timeoutMsg);
				}
			} finally {
				pendingRequests.remove(requestId);
			}
		}
	}
}
