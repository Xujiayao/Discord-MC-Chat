package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.standalone.TerminalManager;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

/**
 * Log command implementation.
 * Retrieves log files and sends them to the sender.
 *
 * @author Xujiayao
 */
public class LogCommand implements Command {

	@Override
	public String name() {
		return "log";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[]{
				new CommandArgument() {
					@Override
					public String name() {
						return "file";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.log.args_desc.file");
					}
				}
		};
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.log.description");
	}

	@Override
	public boolean isVisibleTo(CommandSender sender) {
		// The log command is only meaningful when the sender supports file attachments,
		// i.e., Discord slash commands (JdaCommandSender) or remote execute (capture sender).
		// It should not appear in help for local senders (terminal or Minecraft in-game).
		return !(sender instanceof LocalCommandSender);
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if (sender instanceof TerminalManager.TerminalCommandSender) {
			sender.reply(I18nManager.getDmccTranslation("commands.log.terminal_not_supported"));
			return;
		}

		if (args.length < 1 || args[0].isBlank()) {
			sender.reply(I18nManager.getDmccTranslation("commands.log.usage"));
			return;
		}

		String fileName = args[0];

		byte[] fileData = LogFileUtils.readLogFile(fileName);
		if (fileData == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.log.file_not_found", fileName));
			return;
		}

		// Strip .gz extension for the output file name if it was decompressed
		String outputName = fileName.endsWith(".gz") ? fileName.substring(0, fileName.length() - 3) : fileName;

		sender.replyWithFile(
				I18nManager.getDmccTranslation("commands.log.success", fileName),
				fileData,
				outputName
		);
	}
}
