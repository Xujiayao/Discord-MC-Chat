package com.xujiayao.discord_mc_chat.common.commands;

import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;

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
				response.add("- reload | Reloads DMCC.");
				response.add("- stop   | Shuts down DMCC.");
			}
			case "reload" -> {
				response.add("Reloading DMCC in 5 seconds...");
				EventManager.post(new CommandEvents.ReloadEvent());
			}
			case "stop" -> {
				response.add("Stopping DMCC in 5 seconds...");
				EventManager.post(new CommandEvents.StopEvent());
			}
			default -> response.add("Unknown command: \"" + command + "\". Type \"help\" for a list of commands");
		}

		return response;
	}
}
