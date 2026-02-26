package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provides auto-complete suggestions for DMCC commands.
 * Used on the client side to generate suggestions in response to server requests.
 * <p>
 * Suggestions are filtered based on the sender's OP level to ensure
 * users only see commands they are authorized to execute.
 *
 * @author Xujiayao
 */
public class CommandAutoCompleter {

	/**
	 * Generates auto-complete suggestions based on the current input and sender's OP level.
	 * <p>
	 * The input represents the full command string the user has typed so far
	 * (e.g., "log lat", "hel", "info").
	 *
	 * @param input   The current user input
	 * @param opLevel The OP level of the user requesting auto-complete
	 * @return A list of complete command string suggestions authorized for the user
	 */
	public static List<String> getSuggestions(String input, int opLevel) {
		List<String> suggestions = new ArrayList<>();

		if (input == null || input.isBlank()) {
			// Return all command names authorized for this OP level
			CommandManager.getCommands().stream()
					.sorted(Comparator.comparing(Command::name))
					.forEach(cmd -> addCommandIfAuthorized(cmd, opLevel, suggestions));

			return suggestions;
		}

		String[] parts = input.trim().split("\\s+");
		String commandName = parts[0].toLowerCase();

		if (parts.length == 1 && !input.endsWith(" ")) {
			// User is still typing the command name
			CommandManager.getCommands().stream()
					.filter(cmd -> cmd.name().startsWith(commandName))
					.sorted(Comparator.comparing(Command::name))
					.forEach(cmd -> addCommandIfAuthorized(cmd, opLevel, suggestions));

			return suggestions;
		}

		// User has typed a command name and moved on to arguments
		switch (commandName) {
			case "log" -> {
				if (opLevel >= ConfigManager.getInt("command_permission_levels.log", 4)) {
					String argInput = parts.length > 1 ? parts[1] : "";
					if (parts.length == 1 || (parts.length == 2 && !input.endsWith(" "))) {
						List<String> logFiles = LogFileUtils.listLogFiles();
						String lowerArgInput = argInput.toLowerCase();
						for (String file : logFiles) {
							if (file.toLowerCase().startsWith(lowerArgInput) || file.toLowerCase().contains(lowerArgInput)) {
								suggestions.add("log " + file);
							}
						}
					}
				}
			}
			case "stats" -> {
				if (opLevel >= ConfigManager.getInt("command_permission_levels.stats", 4)) {
					StatsCommand.StatsProvider provider = StatsCommand.getProvider();
					if (provider != null) {
						if (parts.length == 1 || (parts.length == 2 && !input.endsWith(" "))) {
							String typeInput = parts.length > 1 ? parts[1] : "";
							String lowerType = typeInput.toLowerCase();
							for (String type : provider.getStatTypes()) {
								if (type.toLowerCase().startsWith(lowerType) || type.toLowerCase().contains(lowerType)) {
									suggestions.add("stats " + type);
								}
							}
						} else if (parts.length == 2 || (parts.length == 3 && !input.endsWith(" "))) {
							String type = parts[1];
							String statInput = parts.length > 2 ? parts[2] : "";
							String lowerStat = statInput.toLowerCase();
							for (String stat : provider.getStatNames(type)) {
								if (stat.toLowerCase().startsWith(lowerStat) || stat.toLowerCase().contains(lowerStat)) {
									suggestions.add("stats " + type + " " + stat);
								}
							}
						}
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
					addCommandIfAuthorized(command, opLevel, suggestions);
				}
			}
		}

		return suggestions;
	}

	/**
	 * Adds a command's signature to the suggestions list if the sender is authorized to use it.
	 *
	 * @param cmd         The command to check and add.
	 * @param opLevel     The sender's OP level.
	 * @param suggestions The list to append to.
	 */
	private static void addCommandIfAuthorized(Command cmd, int opLevel, List<String> suggestions) {
		int requiredOp = ConfigManager.getInt("command_permission_levels." + cmd.name(), 4);
		if (opLevel >= requiredOp) {
			StringBuilder builder = new StringBuilder();

			builder.append(cmd.name());
			for (Command.CommandArgument arg : cmd.args()) {
				builder.append(" <").append(arg.name()).append(">");
			}

			suggestions.add(builder.toString());
		}
	}
}
