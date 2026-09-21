package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles interactive terminal commands for standalone mode. The terminal listener thread lives for the
 * whole JVM and is not shut down during a reload.
 */
public final class TerminalManager {

	private static final Path LOG_CACHE_DIR = Paths.get("./config/discord_mc_chat/cache/log");

	// Retention for LOG_CACHE_DIR: keep only the newest log dumps, otherwise the directory grows forever.
	private static final int MAX_CACHED_LOG_FILES = 10;

	private TerminalManager() {
	}

	public static void init() {
		// The Scanner is created here and never closed, to keep System.in open.
		Thread terminalThread = new Thread(() -> {
			Scanner scanner = new Scanner(System.in);
			LOGGER.info(I18nManager.getDmccTranslation("terminal.started"));

			while (!Thread.currentThread().isInterrupted()) {
				try {
					if (!scanner.hasNextLine()) {
						// Input exhausted (redirected stdin finished, pipe closed, Ctrl-D, service startup):
						// hasNextLine() would keep returning false forever, so leave the loop instead of
						// spinning at 100% CPU on an EOF stream.
						break;
					}

					String line = scanner.nextLine().trim();

					if (line.startsWith("/")) {
						line = line.substring(1);
					}

					String[] parts = line.split("\\s+");
					String name = parts[0].toLowerCase();
					String[] args;

					// Special handling for 'execute <at> <dmcc_command...>': the dmcc_command part (everything
					// after <at>) is treated as a single argument.
					if ("execute".equals(name) && parts.length >= 3) {
						String at = parts[1];
						int atEndIndex = line.indexOf(parts[1], name.length()) + parts[1].length();
						String command = line.substring(atEndIndex).trim();
						args = new String[]{at, command};
					} else {
						args = parts.length > 1 ? line.substring(line.indexOf(' ') + 1).split("\\s+") : new String[0];
					}

					CommandManager.execute(new TerminalCommandSender(), name, args);
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
	 * File attachments from execute at log commands are saved to the cache directory.
	 */
	public static final class TerminalCommandSender implements LocalCommandSender {
		private TerminalCommandSender() {
		}

		@Override
		public void reply(String message) {
			for (String line : message.split("\n")) {
				LOGGER.info(line);
			}
		}

		@Override
		public void replyWithFile(String message, byte[] fileData, String fileName) {
			reply(message);

			try {
				Files.createDirectories(LOG_CACHE_DIR);
				Path outputPath = LOG_CACHE_DIR.resolve(fileName);
				Files.write(outputPath, fileData);
				LOGGER.info(I18nManager.getDmccTranslation("commands.log.saved_to_cache", outputPath));
				pruneLogCache(outputPath);
			} catch (IOException e) {
				LOGGER.error(I18nManager.getDmccTranslation("commands.log.save_failed", fileName), e);
			}
		}
	}

	/**
	 * Keeps only the newest {@link #MAX_CACHED_LOG_FILES} files in the log cache directory; the file that was
	 * just written counts towards the limit and is never deleted, so a filesystem with a coarse timestamp
	 * resolution cannot remove the dump currently being served. Deliberately silent and best-effort: cleanup
	 * must never break saving a log file, and all DMCC messages are localized resources.
	 */
	private static void pruneLogCache(Path keepPath) {
		try (Stream<Path> entries = Files.list(LOG_CACHE_DIR)) {
			List<Path> cached = entries
					.filter(Files::isRegularFile)
					.filter(path -> !path.equals(keepPath))
					.sorted(Comparator.comparingLong((Path path) -> lastModifiedMillis(path)).reversed())
					.toList();

			for (int i = MAX_CACHED_LOG_FILES - 1; i < cached.size(); i++) {
				try {
					Files.deleteIfExists(cached.get(i));
				} catch (IOException ignored) {
				}
			}
		} catch (IOException ignored) {
			// Cache directory missing or unreadable: nothing to prune.
		}
	}

	private static long lastModifiedMillis(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException ignored) {
			return 0L;
		}
	}
}
