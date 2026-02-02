package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.Comparator;

/**
 * Help command implementation.
 *
 * @author Xujiayao
 */
public class HelpCommand implements Command {

	@Override
	public String name() {
		return "help";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.help.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		StringBuilder builder = new StringBuilder();
		builder.append("========== ").append(I18nManager.getDmccTranslation("commands.help.help")).append(" ==========\n");

		CommandManager.getCommands().stream().sorted(Comparator.comparing(Command::name)).forEach(cmd -> builder
				.append("\n").append("- ").append(cmd.name()).append(": ").append(cmd.description()));

		sender.reply(builder.toString());
	}
}
