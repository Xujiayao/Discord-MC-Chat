package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.Scanner;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles interactive terminal commands for standalone mode.
 * <p>
 * The terminal listener thread runs for the entire lifecycle of the JVM
 * and is not shut down during a reload.
 *
 * @author Xujiayao
 */
public class TerminalManager {

	/**
	 * Initializes and starts the terminal.
	 */
	public static void init() {
		// The Scanner is created here and never closed, to keep System.in open.
		// Arguments do not include the command itself
		// This can happen if System.in is closed externally, which signals the end.
		Thread terminalThread = new Thread(() -> {
			// The Scanner is created here and never closed, to keep System.in open.
			Scanner scanner = new Scanner(System.in);
			LOGGER.info(I18nManager.getDmccTranslation("terminal.started"));

			while (!Thread.currentThread().isInterrupted()) {
				try {
					if (scanner.hasNextLine()) {
						String line = scanner.nextLine();

						// Remove leading slash if present
						if (line.startsWith("/")) {
							line = line.substring(1);
						}

						String[] parts = line.split("\\s+");
						String name = parts[0].toLowerCase();
						String[] args = parts.length > 1 ? line.substring(line.indexOf(' ') + 1).split("\\s+") : new String[0];

						CommandManager.execute(new TerminalCommandSender(), name, args);
					}
				} catch (IllegalStateException e) {
					// This can happen if System.in is closed externally, which signals the end.
					LOGGER.warn(I18nManager.getDmccTranslation("terminal.input_closed"), e);
					break;
				}
			}

			scanner.close();
		}, "DMCC-Terminal");

		// Cannot set as daemon thread. Problematic.
		// terminalThread.setDaemon(true);
		terminalThread.start();
	}

	/**
	 * A CommandSender implementation for terminal commands.
	 *
	 * @author Xujiayao
	 */
	private static class TerminalCommandSender implements CommandSender {

		@Override
		public void reply(String message) {
			// For each line in the message, send a separate log message
			for (String line : message.split("\n")) {
				LOGGER.info(line);
			}
		}
	}
}
