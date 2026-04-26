package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public final class TerminalManager {

	private static final Path LOG_CACHE_DIR = Paths.get("./config/discord_mc_chat/cache/log");

	private TerminalManager() {
	}

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
						String line = scanner.nextLine().trim();

						// Remove leading slash if present
						if (line.startsWith("/")) {
							line = line.substring(1);
						}

						String[] parts = line.split("\\s+");
						String name = parts[0].toLowerCase();
						String[] args;

						// Special handling for 'execute' command:
						// execute <at> <dmcc_command...>
						// The dmcc_command part (everything after <at>) is treated as a single argument.
						if ("execute".equals(name) && parts.length >= 3) {
							String at = parts[1];
							// Everything after "execute <at> " is the command (single argument)
							int atEndIndex = line.indexOf(parts[1], name.length()) + parts[1].length();
							String command = line.substring(atEndIndex).trim();
							args = new String[]{at, command};
						} else {
							args = parts.length > 1 ? line.substring(line.indexOf(' ') + 1).split("\\s+") : new String[0];
						}

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
	 * <p>
	 * File attachments from execute at log commands are saved to the cache directory.
	 *
	 * @author Xujiayao
	 */
	public static final class TerminalCommandSender implements LocalCommandSender {
		private TerminalCommandSender() {
		}

		@Override
		public void reply(String message) {
			// For each line in the message, send a separate log message
			for (String line : message.split("\n")) {
				LOGGER.info(line);
			}
		}

		@Override
		public void replyWithFile(String message, byte[] fileData, String fileName) {
			reply(message);

			// Save the file to the cache directory
			try {
				Files.createDirectories(LOG_CACHE_DIR);
				Path outputPath = LOG_CACHE_DIR.resolve(fileName);
				Files.write(outputPath, fileData);
				LOGGER.info(I18nManager.getDmccTranslation("commands.log.saved_to_cache", outputPath));
			} catch (IOException e) {
				LOGGER.error(I18nManager.getDmccTranslation("commands.log.save_failed", fileName), e);
			}
		}
	}
}
