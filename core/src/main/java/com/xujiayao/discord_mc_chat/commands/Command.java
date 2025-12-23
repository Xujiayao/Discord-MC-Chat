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

		/**
		 * Checks if the argument is optional.
		 *
		 * @return true if the argument is optional, false otherwise.
		 */
		boolean isOptional();
	}
}
