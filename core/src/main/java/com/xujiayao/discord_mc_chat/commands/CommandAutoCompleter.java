package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.utils.LogFileUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides auto-complete suggestions for DMCC commands.
 * Used on the client side to generate suggestions in response to server requests.
 *
 * @author Xujiayao
 */
public class CommandAutoCompleter {

	/**
	 * Generates auto-complete suggestions based on the current input.
	 * <p>
	 * The input represents the full command string the user has typed so far
	 * (e.g., "log lat", "hel", "info").
	 *
	 * @param input The current user input
	 * @return A list of complete command string suggestions
	 */
	public static List<String> getSuggestions(String input) {
		List<String> suggestions = new ArrayList<>();

		if (input == null || input.isBlank()) {
			// Return all command names
			suggestions.addAll(CommandManager.getCommandNames());
			return suggestions;
		}

		String trimmed = input.trim();
		String[] parts = trimmed.split("\\s+", 2);
		String commandName = parts[0].toLowerCase();

		if (parts.length == 1 && !input.endsWith(" ")) {
			// User is still typing the command name — suggest matching command names
			for (String name : CommandManager.getCommandNames()) {
				if (name.startsWith(commandName)) {
					suggestions.add(name);
				}
			}
			return suggestions;
		}

		// User has typed a command name and moved on to arguments
		String argInput = parts.length > 1 ? parts[1] : "";

		switch (commandName) {
			case "log" -> {
				// Suggest log file names
				List<String> logFiles = LogFileUtils.listLogFiles();
				String lowerArgInput = argInput.toLowerCase();
				for (String file : logFiles) {
					if (file.toLowerCase().startsWith(lowerArgInput) || file.toLowerCase().contains(lowerArgInput)) {
						suggestions.add("log " + file);
					}
				}
			}
			default -> {
				// For commands without argument auto-complete, suggest the command itself
				Command command = null;
				for (Command cmd : CommandManager.getCommands()) {
					if (cmd.name().equals(commandName)) {
						command = cmd;
						break;
					}
				}
				if (command != null && command.args().length == 0) {
					suggestions.add(commandName);
				}
			}
		}

		return suggestions;
	}
}
