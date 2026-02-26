package com.xujiayao.discord_mc_chat.commands;

/**
 * Interface representing a DMCC command, with arguments support.
 *
 * @author Xujiayao
 */
public interface Command {

	/**
	 * Gets the name of the command.
	 *
	 * @return The command name.
	 */
	String name();

	/**
	 * Gets the list of arguments for the command.
	 *
	 * @return The command arguments.
	 */
	CommandArgument[] args();

	/**
	 * Gets the description of the command.
	 *
	 * @return The command description.
	 */
	String description();

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
	 *
	 * @param sender The entity that sent the command.
	 * @param args   The command arguments.
	 */
	void execute(CommandSender sender, String... args);

	interface CommandArgument {
		/**
		 * Gets the name of the argument.
		 *
		 * @return The argument name.
		 */
		String name();

		/**
		 * Gets the description of the argument.
		 *
		 * @return The argument description.
		 */
		String description();
	}
}
