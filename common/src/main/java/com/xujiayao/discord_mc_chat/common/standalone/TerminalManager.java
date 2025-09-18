package com.xujiayao.discord_mc_chat.common.standalone;

import java.util.Scanner;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Handles interactive terminal commands for standalone mode.
 *
 * @author Xujiayao
 */
public class TerminalManager {

	private static Thread consoleThread;
	private static volatile boolean running = true;

	/**
	 * Initializes and starts the terminal manager.
	 */
	public static void init() {
		consoleThread = new Thread(() -> {
			LOGGER.info("Interactive terminal started. Type \"help\" for a list of commands");
			Scanner scanner = new Scanner(System.in);

			while (running) {
				if (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					handleCommand(line);
				}
			}
		}, "DMCC-Terminal");

		consoleThread.start();
	}

	/**
	 * Handles a command from the terminal.
	 *
	 * @param commandLine The full command line entered by the user.
	 */
	private static void handleCommand(String commandLine) {
		String[] parts = commandLine.trim().split("\\s+");
		String command = parts[0].toLowerCase();

		switch (command) {
			case "help" -> {
				LOGGER.info("Available DMCC commands:");
				LOGGER.info("  - help   | Shows this help message");
				LOGGER.info("  - reload | Reloads the config.yml and custom message files.");
				LOGGER.info("  - stop   | Shuts down DMCC");
			}
			case "reload" -> {
				LOGGER.info("Reloading DMCC...");
			}
			case "stop" -> {
				LOGGER.info("Stopping DMCC...");
				System.exit(0);
			}
			default -> LOGGER.warn("Unknown command: \"{}\". Type \"help\" for a list of commands", command);
		}
	}

	/**
	 * Shuts down the terminal manager.
	 */
	public static void shutdown() {
		running = false;
		if (consoleThread != null && consoleThread.isAlive()) {
			consoleThread.interrupt();
		}
	}
}
