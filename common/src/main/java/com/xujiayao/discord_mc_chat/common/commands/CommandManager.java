package com.xujiayao.discord_mc_chat.common.commands;

import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.common.utils.i18n.I18nManager;

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
				response.add("==================== " + I18nManager.getDmccTranslation("commands.help.help") + " ====================");
				response.add("- help   | " + I18nManager.getDmccTranslation("commands.help.description"));
				response.add("- reload | " + I18nManager.getDmccTranslation("commands.reload.description"));
				response.add("- stop   | " + I18nManager.getDmccTranslation("commands.stop.description"));
			}
			case "reload" -> EventManager.post(new CommandEvents.ReloadEvent());
			case "stop" -> EventManager.post(new CommandEvents.StopEvent());
			default -> response.add(I18nManager.getDmccTranslation("commands.unknown_command", command));
		}

		return response;

		// TODO 斜杠的话也算一个命令
	}
}
