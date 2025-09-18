package com.xujiayao.discord_mc_chat.common.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the logic and execution of commands from various sources.
 *
 * @author Xujiayao
 */
public class CommandManager {

	/**
	 * Handles a command and returns a list of response messages.
	 *
	 * @param commandLine The full command line.
	 * @return A list of strings representing the response.
	 */
	public static List<String> handleCommand(String commandLine) {
		List<String> response = new ArrayList<>();
		String[] parts = commandLine.trim().split("\\s+");
		String command = parts[0];

		switch (command) {
			case "help" -> {
				response.add("==================== Help ====================");
				response.add("- help   | Shows this help message.");
				response.add("- reload | Reloads the config.yml and custom message files.");
				response.add("- stop   | Shuts down DMCC.");
			}
			case "reload" -> {
				response.add("Reloading DMCC...");
			}
			case "stop" -> {
				response.add("Stopping DMCC...");
				System.exit(0);
			}
			default -> response.add("Unknown command: \"" + command + "\". Type `help` for a list of commands");
		}

		return response;
	}
}
