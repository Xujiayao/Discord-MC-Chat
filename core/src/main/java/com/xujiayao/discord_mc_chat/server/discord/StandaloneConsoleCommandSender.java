package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandSender;

/**
 * Command sender for messages typed in the Standalone DMCC console channel.
 * <p>
 * Replies are sent through the same webhook identity as the console log stream, mirroring how a Minecraft
 * server console channel returns command output with that server's webhook identity.
 *
 * @author Xujiayao
 */
final class StandaloneConsoleCommandSender implements CommandSender {

	private final String channelId;
	private final int opLevel;

	StandaloneConsoleCommandSender(String channelId, int opLevel) {
		this.channelId = channelId;
		this.opLevel = opLevel;
	}

	@Override
	public void reply(String message) {
		DiscordManager.sendExecuteResultViaWebhook(channelId, DiscordManager.STANDALONE_CONSOLE_NAME, message);
	}

	@Override
	public void replyWithFile(String message, byte[] fileData, String fileName) {
		DiscordManager.sendExecuteResultWithFileViaWebhook(channelId, DiscordManager.STANDALONE_CONSOLE_NAME, message, fileData, fileName);
	}

	@Override
	public int getOpLevel() {
		return opLevel;
	}
}
