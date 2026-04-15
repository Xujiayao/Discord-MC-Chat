package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

/**
 * Reload command implementation.
 *
 * @author Xujiayao
 */
public final class ReloadCommand implements Command {

	/**
	 * Creates a reload command instance.
	 */
	public ReloadCommand() {
	}

	@Override
	public String name() {
		return "reload";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.reload.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		sender.reply(I18nManager.getDmccTranslation("commands.reload.reloading"));

		if (DMCC.reload()) {
			sender.reply(I18nManager.getDmccTranslation("commands.reload.success"));
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.reload.failure"));
		}
	}
}
