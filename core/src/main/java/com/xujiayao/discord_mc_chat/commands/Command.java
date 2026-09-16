package com.xujiayao.discord_mc_chat.commands;

/**
 * Interface representing a DMCC command, with arguments support.
 *
 * @author Xujiayao
 */
public interface Command {

	/**
	 * Gets the name of the command.
	 */
	String name();

	/**
	 * Gets the list of arguments for the command.
	 */
	CommandArgument[] args();

	/**
	 * Gets the description of the command.
	 */
	String description();

	/**
	 * Gets the list of arguments for the command, potentially adjusted for the sender context.
	 * <p>
	 * By default, delegates to {@link #args()}. Commands can override this to
	 * show different arguments based on sender type (e.g., Discord vs Minecraft).
	 *
	 * @param sender The command sender requesting the help listing.
	 * @return The command arguments appropriate for the given sender.
	 */
	default CommandArgument[] argsForSender(CommandSender sender) {
		return args();
	}

	/**
	 * Whether this command accepts more arguments than defined by {@link #args()}.
	 * <p>
	 * Override and return true for commands that handle variable-length arguments
	 * internally (e.g., execute, whose "command" arg may itself contain spaces).
	 *
	 * @return true if extra arguments are allowed, false otherwise.
	 */
	default boolean acceptsExtraArgs() {
		return false;
	}

	/**
	 * Whether this command should appear in auto-complete suggestions for the execute command.
	 * <p>
	 * Commands that are only meaningful for specific sender types (e.g., link/unlink
	 * which require player or Discord context) should return false.
	 * This does NOT affect whether the command can be executed — only its auto-complete visibility.
	 *
	 * @return true if this command should appear in auto-complete, false otherwise.
	 */
	default boolean isAutoCompletable() {
		return true;
	}

	/**
	 * Whether this command should be visible in help listings for the given sender.
	 * <p>
	 * Commands can override this to hide themselves from certain sender types.
	 * This does NOT affect whether the command can be executed — only its visibility in help.
	 *
	 * @param sender The command sender requesting the help listing.
	 * @return true if this command should be shown to the sender, false otherwise.
	 */
	default boolean isVisibleInHelp(CommandSender sender) {
		return true;
	}

	/**
	 * Whether this command should be visible in help when the request originates
	 * from a local Minecraft sender context.
	 * <p>
	 * This is evaluated only in Minecraft environment for local senders.
	 * It does NOT affect whether the command can be executed — only its visibility in help.
	 *
	 * @return true if this command should be shown in Minecraft local help, false otherwise.
	 */
	default boolean isVisibleFromMinecraft() {
		return true;
	}

	/**
	 * Executes the command.
	 */
	void execute(CommandSender sender, String... args);

	/**
	 * Builds the {@code name <arg> <arg>} usage string shown in help, auto-complete and argument errors.
	 *
	 * @param args The arguments to render.
	 * @return The usage string without a leading slash.
	 */
	default String usage(CommandArgument... args) {
		StringBuilder usage = new StringBuilder(name());
		for (CommandArgument arg : args) {
			usage.append(" <").append(arg.name()).append(">");
		}
		return usage.toString();
	}

	/**
	 * Describes a single command argument displayed in help and usage text.
	 */
	record CommandArgument(String name, String description) {
	}
}
