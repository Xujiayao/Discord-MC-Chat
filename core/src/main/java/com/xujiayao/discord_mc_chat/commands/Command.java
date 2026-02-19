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
