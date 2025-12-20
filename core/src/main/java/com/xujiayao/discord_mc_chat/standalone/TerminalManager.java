package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.commands.CommandManager;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles interactive terminal commands for standalone mode.
 * The terminal listener thread runs for the entire lifecycle of the JVM
 * and is not shut down during a reload to avoid closing System.in.
 *
 * @author Xujiayao
 */
public class TerminalManager {

	private static final AtomicBoolean running = new AtomicBoolean(false);
	private static final AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * Initializes and starts the terminal manager if it hasn't been already.
	 * This method is safe to call multiple times.
	 */
	public static void init() {
		if (initialized.compareAndSet(false, true)) {
			// This block runs only once for the lifetime of the application.
			// The Scanner is created here and never closed, to keep System.in open.
			// Only process commands if the TerminalManager is in a "running" state.
			// This can happen if System.in is closed externally, which signals the end.
			Thread terminalThread = new Thread(() -> {
				// The Scanner is created here and never closed, to keep System.in open.
				Scanner scanner = new Scanner(System.in);
				LOGGER.info("Interactive terminal started. Type \"/help\" for a list of available commands.");

				while (!Thread.currentThread().isInterrupted()) {
					try {
						if (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							// Only process commands if the TerminalManager is in a "running" state.
							if (running.get()) {
								List<String> response = CommandManager.handleCommand(line);
								for (String message : response) {
									LOGGER.info(message);
								}
							}
						}
					} catch (IllegalStateException e) {
						// This can happen if System.in is closed externally, which signals the end.
						LOGGER.warn("Terminal input stream closed. Shutting down terminal manager.", e);
						break;
					}
				}
			}, "DMCC-Terminal");

			terminalThread.start();
		}

		// Set the running state to true, allowing the loop to process commands.
		running.set(true);
	}

	/**
	 * Stops processing commands, but does not shut down the underlying listener thread.
	 * This is called during a reload.
	 */
	public static void shutdown() {
		// This simply stops the loop from processing new commands.
		// The thread and Scanner remain active to be reused after reload.
		running.set(false);
	}
}
