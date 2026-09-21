package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.LinkCommand;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.concurrent.RejectedExecutionException;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

public final class JdaCommandSender implements CommandSender, LinkCommand.DiscordUserContextProvider {

	private final SlashCommandInteractionEvent event;
	private final int opLevel;

	public JdaCommandSender(SlashCommandInteractionEvent event, int opLevel) {
		this.event = event;
		this.opLevel = opLevel;
	}

	@Override
	public void reply(String message) {
		try {
			// Use complete() instead of queue() to ensure the message is actually sent
			// before the code proceeds. This is critical for commands like 'reload' or 'shutdown'
			// which might kill the JDA connection immediately after this method returns.
			for (String block : CodeBlockMessageUtils.splitToCodeBlocks(message)) {
				event.getHook().sendMessage(block).complete();
			}
		} catch (RejectedExecutionException e) {
			// This usually happens when trying to send the "Success" message after a reload/shutdown,
			// because the JDA instance belonging to this event has been shut down.
			// We log it as a warning but don't crash the thread, state that it is expected behavior.
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed"));
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed_detail"));
		}
	}

	@Override
	public void replyWithFile(String message, byte[] fileData, String fileName) {
		try {
			var blocks = CodeBlockMessageUtils.splitToCodeBlocks(message);
			event.getHook().sendMessage(blocks.getFirst())
					.addFiles(FileUpload.fromData(fileData, fileName))
					.complete();
			for (int i = 1; i < blocks.size(); i++) {
				event.getHook().sendMessage(blocks.get(i)).complete();
			}
		} catch (RejectedExecutionException e) {
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed"));
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed_detail"));
		}
	}

	public Member getMember() {
		return event.getMember();
	}

	public User getUser() {
		return event.getUser();
	}

	@Override
	public int getOpLevel() {
		return opLevel;
	}

	@Override
	public String getDiscordUserId() {
		return event.getUser().getId();
	}

	@Override
	public String getDiscordUserName() {
		return event.getUser().getName();
	}

	public String getChannelId() {
		return event.getChannel().getId();
	}
}
