package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

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

		if (commandLine.startsWith("/")) {
			commandLine = commandLine.substring(1);
		}

		String[] parts = commandLine.trim().split("\\s+");
		String command = parts[0];

		switch (command) {
			case "help" -> {
				response.add("==================== " + I18nManager.getDmccTranslation("commands.help.help") + " ====================");
				response.add("- help     | " + I18nManager.getDmccTranslation("commands.help.description"));
				response.add("- reload   | " + I18nManager.getDmccTranslation("commands.reload.description"));
				response.add("- shutdown | " + I18nManager.getDmccTranslation("commands.shutdown.description"));
			}
			case "reload" -> EventManager.post(new CommandEvents.ReloadEvent());
			case "shutdown" -> EventManager.post(new CommandEvents.ShutdownEvent());
			default -> response.add(I18nManager.getDmccTranslation("commands.unknown_command", command));
		}

		return response;
	}
}
