package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.ConsoleLogBatchPacket;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Tails logs/latest.log on the client and forwards appended lines in batches.
 */
final class ConsoleLogTailer {

	private static final long POLL_INTERVAL_MS = 1000;
	private static final int MAX_LINES_PER_BATCH = 80;
	private static final int MAX_CHARS_PER_BATCH = 6000;

	private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

	private static ScheduledExecutorService executor;
	private static RandomAccessFile reader;
	private static File currentFile;
	private static long pointer;

	private ConsoleLogTailer() {
	}

	static synchronized void updateEnabled(boolean enabled) {
		if (enabled) {
			ENABLED.set(true);
			startIfNeeded();
		} else {
			ENABLED.set(false);
			stop();
		}
	}

	static synchronized void stop() {
		closeReader();
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private static void startIfNeeded() {
		if (executor != null && !executor.isShutdown()) {
			return;
		}
		executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "DMCC-ConsoleLogTailer"));
		executor.scheduleWithFixedDelay(ConsoleLogTailer::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private static void poll() {
		if (!ENABLED.get()) {
			return;
		}

		try {
			ensureReader();
			if (reader == null) {
				return;
			}

			if (currentFile == null || !currentFile.exists()) {
				closeReader();
				return;
			}

			long fileLength = currentFile.length();
			if (fileLength < pointer) {
				// latest.log rotated or truncated: reopen and continue from start of new file.
				reopenReader(false);
			}

			if (reader == null) {
				return;
			}

			reader.seek(pointer);
			List<String> lines = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
				if (!utf8.isBlank()) {
					lines.add(utf8);
				}
			}
			pointer = reader.getFilePointer();

			sendBatches(lines);
		} catch (Exception e) {
			LOGGER.warn(I18nManager.getDmccTranslation("client.console_log_tailer.poll_failed", e.getMessage()));
			closeReader();
		}
	}

	private static void ensureReader() {
		if (reader != null) {
			return;
		}
		reopenReader(true);
	}

	private static void reopenReader(boolean skipHistory) {
		closeReader();
		try {
			File latestLog = Path.of("logs", "latest.log").toFile();
			if (!latestLog.exists()) {
				return;
			}
			currentFile = latestLog;
			reader = new RandomAccessFile(latestLog, "r");
			pointer = skipHistory ? latestLog.length() : 0;
			reader.seek(pointer);
		} catch (Exception e) {
			closeReader();
		}
	}

	private static void sendBatches(List<String> lines) {
		if (lines == null || lines.isEmpty()) {
			return;
		}

		List<String> batch = new ArrayList<>();
		int chars = 0;
		for (String line : lines) {
			String safeLine = line == null ? "" : line;
			if (safeLine.length() > MAX_CHARS_PER_BATCH) {
				safeLine = safeLine.substring(0, MAX_CHARS_PER_BATCH);
			}

			int nextChars = chars + safeLine.length() + 1;
			if (!batch.isEmpty() && (batch.size() >= MAX_LINES_PER_BATCH || nextChars > MAX_CHARS_PER_BATCH)) {
				NetworkManager.sendPacketToServer(new ConsoleLogBatchPacket(List.copyOf(batch)));
				batch.clear();
				chars = 0;
			}

			batch.add(safeLine);
			chars += safeLine.length() + 1;
		}

		NetworkManager.sendPacketToServer(new ConsoleLogBatchPacket(List.copyOf(batch)));
	}

	private static void closeReader() {
		if (reader != null) {
			try {
				reader.close();
			} catch (Exception ignored) {
			}
		}
		reader = null;
		currentFile = null;
		pointer = 0;
	}
}
