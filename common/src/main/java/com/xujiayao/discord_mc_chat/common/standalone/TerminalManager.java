package com.xujiayao.discord_mc_chat.common.standalone;

import com.xujiayao.discord_mc_chat.common.commands.CommandManager;

import java.util.List;
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
		running = true;

		consoleThread = new Thread(() -> {
			LOGGER.info("Interactive terminal started. Type \"help\" for a list of commands");
			Scanner scanner = new Scanner(System.in);

			while (running) {
				if (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					List<String> response = CommandManager.handleCommand(line);
					for (String message : response) {
						LOGGER.info(message);
					}
				}
			}
		}, "DMCC-Terminal");

		consoleThread.start();
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
