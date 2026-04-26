package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.update.UpdateCheckManager;

/**
 * Update command implementation.
 *
 * @author Xujiayao
 */
public final class UpdateCommand implements Command {

	/**
	 * Creates an update command instance.
	 */
	public UpdateCommand() {
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
		sender.reply(UpdateCheckManager.checkNow());
	}
}
