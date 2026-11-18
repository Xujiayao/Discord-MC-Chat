package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.logging.impl.LoggerImpl;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Mirrors the Standalone DMCC console (its own log file) to the configured Discord channel.
 * <p>
 * This mirrors how a Minecraft client's console channel works: the log file is tailed from its beginning
 * (so the channel receives startup history and stack traces too), lines are forwarded in batches through the
 * same redaction/inline-code pipeline, and start/stop notices are sent around the forwarder's lifetime.
 * Incoming messages in that channel are handled separately by {@code DiscordEventHandler} as terminal commands.
 *
 * @author Xujiayao
 */
public final class StandaloneConsoleForwarder {

	private static final long POLL_INTERVAL_MS = 1000;
	private static final int MAX_LINES_PER_BATCH = 80;
	private static final int MAX_CHARS_PER_BATCH = 6000;
	private static final int MAX_PENDING_LINES = 1000;

	private static final Deque<String> PENDING = new ArrayDeque<>();
	private static ScheduledExecutorService executor;
	private static volatile boolean disabled;
	private static Object currentFileKey;
	private static long pointer;

	private StandaloneConsoleForwarder() {
	}

	public static synchronized void start() {
		if (executor != null && !executor.isShutdown()) {
			return;
		}

		disabled = false;

		if (!DiscordManager.sendStandaloneConsoleStatusMessage(true)) {
			disabled = true;
			LOGGER.warn(I18nManager.getDmccTranslation("standalone_console.disabled",
					ConfigManager.getString("console_forwarding.channel_for_standalone", "")));
			return;
		}

		executor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-StandaloneConsole"));
		executor.scheduleWithFixedDelay(StandaloneConsoleForwarder::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	public static synchronized void shutdown() {
		boolean wasRunning = executor != null;
		disabled = true;
		if (executor != null) {
			executor.shutdownNow();
			try {
				executor.awaitTermination(2, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			executor = null;
		}
		clearPending();
		if (wasRunning) {
			DiscordManager.sendStandaloneConsoleStatusMessage(false);
		}
	}

	private static void poll() {
		if (disabled || !isEnabledForStandalone()) {
			clearPending();
			return;
		}

		try {
			Path logPath = LoggerImpl.getCurrentLogFilePath();
			if (logPath == null || !Files.exists(logPath)) {
				currentFileKey = null;
				return;
			}

			Object latestKey = readFileKey(logPath);
			long latestLength = Files.size(logPath);

			boolean replaced = currentFileKey != null && latestKey != null && !currentFileKey.equals(latestKey);
			boolean truncated = latestLength < pointer;
			if (replaced || truncated) {
				pointer = 0;
				clearPending();
			}

			readAvailableLines(logPath);
			currentFileKey = latestKey;

			flushPendingBatches();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("standalone_console.failed", e.getMessage()), e);
		}
	}

	private static boolean isEnabledForStandalone() {
		if (!"standalone".equals(ModeManager.getMode())) {
			return false;
		}
		if (!ConfigManager.getBoolean("console_forwarding.enable", false)) {
			return false;
		}
		String channel = ConfigManager.getString("console_forwarding.channel_for_standalone", "");
		return channel != null && !channel.isBlank();
	}

	private static void readAvailableLines(Path logPath) throws IOException {
		try (RandomAccessFile reader = new RandomAccessFile(logPath.toFile(), "r")) {
			long length = reader.length();
			if (pointer > length) {
				pointer = 0;
			}

			reader.seek(pointer);
			String line;
			while ((line = reader.readLine()) != null) {
				String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
				if (!utf8.isBlank()) {
					addPendingLine(utf8.length() > MAX_CHARS_PER_BATCH ? utf8.substring(0, MAX_CHARS_PER_BATCH) : utf8);
				}
			}
			pointer = reader.getFilePointer();
		}
	}

	private static void addPendingLine(String line) {
		synchronized (PENDING) {
			PENDING.addLast(line);
			// Bounded buffer: drop the oldest lines instead of growing without limit while Discord is unreachable.
			while (PENDING.size() > MAX_PENDING_LINES) {
				PENDING.removeFirst();
			}
		}
	}

	private static void flushPendingBatches() {
		while (!disabled) {
			if (DiscordManager.hasStandaloneConsoleSendFailed()) {
				disabled = true;
				clearPending();
				LOGGER.warn(I18nManager.getDmccTranslation("standalone_console.send_failed"));
				return;
			}
			List<String> batch = new ArrayList<>();
			int chars = 0;

			synchronized (PENDING) {
				if (PENDING.isEmpty()) {
					return;
				}

				while (!PENDING.isEmpty()) {
					String line = PENDING.peekFirst();
					int nextChars = chars + line.length() + 1;
					if (!batch.isEmpty() && (batch.size() >= MAX_LINES_PER_BATCH || nextChars > MAX_CHARS_PER_BATCH)) {
						break;
					}
					batch.add(PENDING.removeFirst());
					chars = nextChars;
				}
			}

			if (!DiscordManager.sendStandaloneConsoleBatch(batch)) {
				disabled = true;
				clearPending();
				LOGGER.warn(I18nManager.getDmccTranslation("standalone_console.disabled",
						ConfigManager.getString("console_forwarding.channel_for_standalone", "")));
				return;
			}
		}
	}

	private static void clearPending() {
		synchronized (PENDING) {
			PENDING.clear();
		}
	}

	private static Object readFileKey(Path logPath) {
		try {
			return Files.readAttributes(logPath, BasicFileAttributes.class).fileKey();
		} catch (Exception ignored) {
			return null;
		}
	}
}
