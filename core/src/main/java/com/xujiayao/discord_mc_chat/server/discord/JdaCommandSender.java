package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.concurrent.RejectedExecutionException;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Command sender implementation for JDA slash commands.
 * <p>
 * For the execute command, the actual results are sent via webhooks by DiscordManager.
 * This sender only provides the ephemeral acknowledgement to the slash command invoker.
 *
 * @author Xujiayao
 */
public class JdaCommandSender implements CommandSender {

	private final SlashCommandInteractionEvent event;

	public JdaCommandSender(SlashCommandInteractionEvent event) {
		this.event = event;
	}

	@Override
	public void reply(String message) {
		try {
			// Use complete() instead of queue() to ensure the message is actually sent
			// before the code proceeds. This is critical for commands like 'reload' or 'shutdown'
			// which might kill the JDA connection immediately after this method returns.
			event.getHook().sendMessage("```" + message + "```").complete();
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
			event.getHook().sendMessage("```" + message + "```")
					.addFiles(FileUpload.fromData(fileData, fileName))
					.complete();
		} catch (RejectedExecutionException e) {
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed"));
			LOGGER.warn(I18nManager.getDmccTranslation("discord.command.reply_failed_detail"));
		}
	}
}
