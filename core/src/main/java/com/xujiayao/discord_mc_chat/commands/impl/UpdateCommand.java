package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.update.UpdateCheckManager;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Update command implementation.
 *
 * @author Xujiayao
 */
public final class UpdateCommand implements Command {

	private static final int UPDATE_TIMEOUT_SECONDS = 30;
	private static final Map<String, CompletableFuture<CommandPackets.Update.ResponsePacket>> pendingRequests = new ConcurrentHashMap<>();

	/**
	 * Creates an update command instance.
	 */
	public UpdateCommand() {
	}

	/**
	 * Completes a pending update request with the given response.
	 *
	 * @param requestId The request ID.
	 * @param response  The response packet.
	 */
	public static void completeRequest(String requestId, CommandPackets.Update.ResponsePacket response) {
		CompletableFuture<CommandPackets.Update.ResponsePacket> future = pendingRequests.remove(requestId);
		if (future != null && !future.isDone()) {
			future.complete(response);
		}
	}

	private static void replyResult(CommandSender sender, String message) {
		String output = sender instanceof LocalCommandSender
				? DiscordMessageParser.formatDiscordTimestampsForPlainText(message)
				: message;
		sender.reply(output);
	}

	@Override
	public String name() {
		return "update";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.update.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if ("multi_server_client".equals(ModeManager.getMode())) {
			if (NetworkManager.getClient() != null && NetworkManager.getClient().isConnected()) {
				sender.reply(I18nManager.getDmccTranslation("commands.update.checking"));
				String requestId = CryptUtils.generateRandomString(16);
				CompletableFuture<CommandPackets.Update.ResponsePacket> future = new CompletableFuture<>();
				pendingRequests.put(requestId, future);
				NetworkManager.sendPacketToServer(new CommandPackets.Update.RequestPacket(requestId));

				try {
					CommandPackets.Update.ResponsePacket response = future.get(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
					replyResult(sender, response.response);
				} catch (Exception e) {
					pendingRequests.remove(requestId);
					sender.reply(I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage()));
				}
			} else {
				sender.reply(I18nManager.getDmccTranslation("commands.update.server_unavailable"));
			}
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.update.checking"));
			UpdateCheckManager.CheckResult result = UpdateCheckManager.checkNow();
			replyResult(sender, result.message());
		}
	}
}
