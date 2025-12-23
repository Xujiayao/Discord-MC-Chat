package com.xujiayao.discord_mc_chat.standalone;

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

						if (line.startsWith("/")) {
							line = line.substring(1);
						}

						String command = line.trim().split("\\s+")[0];
						// Arguments do not include the command itself
						String[] args = line.trim().split("\\s+").length > 1
								? line.trim().split("\\s+", 2)[1].split("\\s+")
								: new String[0];

						// TODO Handle command
						if (true) {
							LOGGER.error(I18nManager.getDmccTranslation("terminal.unknown_command", line));
						}
					}
				} catch (IllegalStateException e) {
					// This can happen if System.in is closed externally, which signals the end.
					LOGGER.warn("Terminal input stream closed. Shutting down terminal manager.", e);
					break;
				}
			}

			scanner.close();
		}, "DMCC-Terminal");

		terminalThread.setDaemon(true);
		terminalThread.start();
	}
}
