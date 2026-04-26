package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.util.Comparator;
import java.util.List;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;

/**
 * Help command implementation.
 * <p>
 * Dynamically displays only the commands the sender is authorized to execute,
 * based on their OP level and the local permission configuration.
 *
 * @author Xujiayao
 */
public final class HelpCommand implements Command {

	/**
	 * Creates a help command instance.
	 */
	public HelpCommand() {
	}

	private static String padRight(String text, int width) {
		if (text.length() >= width) {
			return text;
		}
		return text + " ".repeat(width - text.length());
	}

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
		builder.append("========== ")
				.append(I18nManager.getDmccTranslation("commands.help.help"))
				.append(" ==========\n");

		boolean isFromMinecraft = (IS_MINECRAFT_ENV && sender instanceof LocalCommandSender);
		String mcPrefix = isFromMinecraft ? "dmcc " : "";

		List<Command> visibleCommands = CommandManager.getCommands().stream()
				.filter(cmd -> cmd.isVisibleInHelp(sender))
				.filter(cmd -> !isFromMinecraft || cmd.isVisibleFromMinecraft())
				.filter(cmd -> sender.getOpLevel() >= ConfigManager.getInt("command_permission_levels." + cmd.name(), 4))
				.sorted(Comparator.comparing(Command::name))
				.toList();

		// Calculate max width for left column to align descriptions
		int maxLeftWidth = 0;
		for (Command cmd : visibleCommands) {
			String commandLabel = "- " + mcPrefix + cmd.name();
			maxLeftWidth = Math.max(maxLeftWidth, commandLabel.length());

			Command.CommandArgument[] arguments = cmd.argsForSender(sender);
			for (int i = 0; i < arguments.length; i++) {
				boolean isLast = (i == arguments.length - 1);
				String branch = isLast ? "  └─ " : "  ├─ ";
				String argLabel = branch + "<" + arguments[i].name() + ">";
				maxLeftWidth = Math.max(maxLeftWidth, argLabel.length());
			}
		}

		// Build the help message with proper alignment
		for (Command cmd : visibleCommands) {
			String commandLabel = "- " + mcPrefix + cmd.name();
			builder.append("\n")
					.append(padRight(commandLabel, maxLeftWidth))
					.append(" | ")
					.append(cmd.description());

			Command.CommandArgument[] arguments = cmd.argsForSender(sender);
			for (int i = 0; i < arguments.length; i++) {
				boolean isLast = (i == arguments.length - 1);
				String branch = isLast ? "  └─ " : "  ├─ ";
				String argLabel = branch + "<" + arguments[i].name() + ">";
				builder.append("\n")
						.append(padRight(argLabel, maxLeftWidth))
						.append(" | ")
						.append(arguments[i].description());
			}
		}

		sender.reply(builder.toString());
	}
}
