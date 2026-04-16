package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * Command sender for text-channel messages that trigger DMCC commands.
 */
public final class MessageCommandSender implements CommandSender {

	private final MessageReceivedEvent event;
	private final int opLevel;

	/**
	 * Creates a command sender wrapper for a Discord message event.
	 *
	 * @param event   Source Discord message event.
	 * @param opLevel Resolved DMCC OP level for command authorization.
	 */
	public MessageCommandSender(MessageReceivedEvent event, int opLevel) {
		this.event = event;
		this.opLevel = opLevel;
	}

	@Override
	public void reply(String message) {
		for (String block : CodeBlockMessageUtils.splitToCodeBlocks(message)) {
			event.getChannel().sendMessage(block).queue();
		}
	}

	@Override
	public void replyWithFile(String message, byte[] fileData, String fileName) {
		var blocks = CodeBlockMessageUtils.splitToCodeBlocks(message);
		event.getChannel().sendMessage(blocks.getFirst())
				.addFiles(FileUpload.fromData(fileData, fileName))
				.queue();
		for (int i = 1; i < blocks.size(); i++) {
			event.getChannel().sendMessage(blocks.get(i)).queue();
		}
	}

	@Override
	public int getOpLevel() {
		return opLevel;
	}

	/**
	 * Gets the Discord channel ID where this command was invoked.
	 *
	 * @return The channel ID.
	 */
	public String getChannelId() {
		return event.getChannel().getId();
	}
}
