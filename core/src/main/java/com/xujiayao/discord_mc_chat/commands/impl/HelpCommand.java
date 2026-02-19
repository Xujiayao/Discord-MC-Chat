package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.Comparator;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;

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

		String mcPrefix = (IS_MINECRAFT_ENV && sender instanceof LocalCommandSender) ? "dmcc " : "";

		CommandManager.getCommands().stream()
				.filter(cmd -> cmd.isVisibleTo(sender))
				.sorted(Comparator.comparing(Command::name))
				.forEach(cmd -> {
					builder.append("\n").append("- ").append(mcPrefix).append(cmd.name()).append(": ").append(cmd.description());

					for (Command.CommandArgument arg : cmd.args()) {
						builder.append("\n    ").append("<").append(arg.name()).append(">: ").append(arg.description());
					}
				});

		sender.reply(builder.toString());
	}
}
