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

		String trimmed = input.trim();
		String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
		boolean hasTrailingSpace = input.endsWith(" ");

		if (parts.length == 0) {
			return suggestions;
		}

		String commandName = parts[0].toLowerCase();

		// User is typing command name (no trailing space yet)
		if (parts.length == 1 && !hasTrailingSpace) {
			// Special-case commands with argument auto-complete even when command name is exact.
			switch (commandName) {
				case "stats" -> {
					return suggestStats(parts, false, opLevel);
				}
				case "log" -> {
					return suggestLog(parts, false, opLevel);
				}
				case "whitelist" -> {
					return suggestWhitelist(parts, false, opLevel);
				}
			}

			CommandManager.getCommands().stream()
					.filter(cmd -> cmd.name().startsWith(commandName))
					.filter(Command::isAutoCompletable)
					.sorted(Comparator.comparing(Command::name))
					.forEach(cmd -> addCommandIfAuthorized(cmd, opLevel, suggestions));

			return suggestions;
		}

		// Command + args stage
		return switch (commandName) {
			case "stats" -> suggestStats(parts, hasTrailingSpace, opLevel);
			case "log" -> suggestLog(parts, hasTrailingSpace, opLevel);
			case "whitelist" -> suggestWhitelist(parts, hasTrailingSpace, opLevel);
			default -> {
				// For commands without argument auto-complete, suggest the command itself if no args.
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
				yield suggestions;
			}
		};
	}

	private static List<String> suggestLog(String[] parts, boolean hasTrailingSpace, int opLevel) {
		List<String> suggestions = new ArrayList<>();
		if (opLevel < ConfigManager.getInt("command_permission_levels.log", 4)) {
			return suggestions;
		}

		List<String> logFiles = LogFileUtils.listLogFiles();

		// "log" (exact, no space) or "log " => first-argument suggestions
		if (parts.length == 1) {
			for (String file : logFiles) {
				suggestions.add("log " + file);
			}
			return suggestions;
		}

		// "log <file...>"
		if (parts.length == 2) {
			String fileInput = parts[1];
			String lowerInput = fileInput.toLowerCase();

			List<String> exact = new ArrayList<>();
			List<String> prefix = new ArrayList<>();
			List<String> contains = new ArrayList<>();

			for (String file : logFiles) {
				String lower = file.toLowerCase();
				if (lower.equals(lowerInput)) {
					exact.add(file);
				} else if (lower.startsWith(lowerInput)) {
					prefix.add(file);
				} else if (lower.contains(lowerInput)) {
					contains.add(file);
				}
			}

			// Strategy: show longer prefix candidates first, then exact match.
			// For single-argument commands, exact match should still be included.
			for (String file : prefix) {
				suggestions.add("log " + file);
			}
			for (String file : contains) {
				suggestions.add("log " + file);
			}
			for (String file : exact) {
				suggestions.add("log " + file);
			}
			return suggestions;
		}

		// More than one argument: log has no additional arguments
		return suggestions;
	}

	private static List<String> suggestStats(String[] parts, boolean hasTrailingSpace, int opLevel) {
		List<String> suggestions = new ArrayList<>();
		if (opLevel < ConfigManager.getInt("command_permission_levels.stats", 4)) {
			return suggestions;
		}

		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null) {
			return suggestions;
		}

		// "stats" (exact, no space) or "stats " => first-argument suggestions
		if (parts.length == 1) {
			for (String type : provider.getStatTypes()) {
				suggestions.add("stats " + type + " <stat>");
			}
			return suggestions;
		}

		// Typing first argument: "stats <type...>"
		if (parts.length == 2 && !hasTrailingSpace) {
			String typeInput = parts[1];
			String lowerType = typeInput.toLowerCase();

			List<String> exactType = new ArrayList<>();
			List<String> prefixType = new ArrayList<>();
			List<String> containsType = new ArrayList<>();

			for (String type : provider.getStatTypes()) {
				String lower = type.toLowerCase();
				if (lower.equals(lowerType)) {
					exactType.add(type);
				} else if (lower.startsWith(lowerType)) {
					prefixType.add(type);
				} else if (lower.contains(lowerType)) {
					containsType.add(type);
				}
			}

			// Before a trailing space is entered, show possible longer type matches first.
			for (String type : prefixType) {
				suggestions.add("stats " + type + " <stat>");
			}
			for (String type : containsType) {
				suggestions.add("stats " + type + " <stat>");
			}

			// On exact type match, start second-argument suggestions immediately
			// (but keep them after longer prefix candidates).
			if (!exactType.isEmpty()) {
				String type = exactType.getFirst();
				for (String stat : provider.getStatNames(type)) {
					suggestions.add("stats " + type + " " + stat);
				}
			}

			return suggestions;
		}

		// "stats <type> "
		if (parts.length == 2) {
			String type = parts[1];
			for (String stat : provider.getStatNames(type)) {
				suggestions.add("stats " + type + " " + stat);
			}
			return suggestions;
		}

		// Typing second argument: "stats <type> <stat...>"
		if (parts.length == 3 && !hasTrailingSpace) {
			String type = parts[1];
			String statInput = parts[2];
			String lowerStat = statInput.toLowerCase();

			List<String> exactStat = new ArrayList<>();
			List<String> prefixStat = new ArrayList<>();
			List<String> containsStat = new ArrayList<>();

			for (String stat : provider.getStatNames(type)) {
				String lower = stat.toLowerCase();
				if (lower.equals(lowerStat)) {
					exactStat.add(stat);
				} else if (lower.startsWith(lowerStat)) {
					prefixStat.add(stat);
				} else if (lower.contains(lowerStat)) {
					containsStat.add(stat);
				}
			}

			// Before trailing space, show longer prefix candidates first, then exact.
			// Exact should still be included.
			for (String stat : prefixStat) {
				suggestions.add("stats " + type + " " + stat);
			}
			for (String stat : containsStat) {
				suggestions.add("stats " + type + " " + stat);
			}
			for (String stat : exactStat) {
				suggestions.add("stats " + type + " " + stat);
			}

			return suggestions;
		}

		// "stats <type> <stat> " or more arguments (stats only has 2 args)
		return suggestions;
	}

	private static List<String> suggestWhitelist(String[] parts, boolean hasTrailingSpace, int opLevel) {
		List<String> suggestions = new ArrayList<>();
		if (opLevel < ConfigManager.getInt("command_permission_levels.whitelist", 4)) {
			return suggestions;
		}

		// whitelist / whitelist  => always show template
		if (parts.length == 1) {
			suggestions.add("whitelist <player>");
			return suggestions;
		}

		// whitelist a => only echo user's current input
		if (parts.length == 2) {
			if (hasTrailingSpace) {
				// whitelist a<space> means an unknown second argument slot appears, return empty.
				return suggestions;
			}
			suggestions.add("whitelist " + parts[1]);
			return suggestions;
		}

		// Extra arguments appear (e.g. whitelist a b) => empty
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
		if (!cmd.isAutoCompletable()) {
			return;
		}
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
