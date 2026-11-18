package com.xujiayao.discord_mc_chat.commands;

import java.util.Arrays;
import java.util.Locale;

/**
 * Parses a DMCC command line the same way for the interactive terminal and the Standalone console channel.
 * <p>
 * The only special case is `execute <at> <dmcc_command...>`: everything after `<at>` is passed as a single
 * argument so that commands containing spaces reach the target client unchanged.
 *
 * @author Xujiayao
 */
public final class CommandLineParser {

	/**
	 * @param name Lower-cased command name
	 * @param args Command arguments
	 */
	public record ParsedCommand(String name, String[] args) {
	}

	private CommandLineParser() {
	}

	public static ParsedCommand parse(String rawLine) {
		String line = rawLine == null ? "" : rawLine.trim();

		if (line.startsWith("/")) {
			line = line.substring(1);
		}

		if (line.isBlank()) {
			return new ParsedCommand("", new String[0]);
		}

		String[] parts = line.split("\\s+");
		String name = parts[0].toLowerCase(Locale.ROOT);
		String[] args;

		if ("execute".equals(name) && parts.length >= 3) {
			String at = parts[1];
			int atEndIndex = line.indexOf(parts[1], name.length()) + parts[1].length();
			String command = line.substring(atEndIndex).trim();
			args = new String[]{at, command};
		} else {
			args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
		}

		return new ParsedCommand(name, args);
	}
}
