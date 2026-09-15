package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Tails logs/latest.log on the client and forwards lines in batches.
 * <p>
 * Design notes:
 * <ul>
 *   <li>The listener stays alive across network reconnects.</li>
 *   <li>New lines are buffered while disconnected and flushed after reconnect.</li>
 *   <li>On first enable, history is replayed from the start of latest.log.</li>
 *   <li>The buffer is bounded: while a long disconnect keeps filling it, the oldest lines are dropped so a
 *       stuck client cannot grow the buffer without limit.</li>
 * </ul>
 *
 * @author Xujiayao
 */
final class ConsoleLogTailer {

	private static final long POLL_INTERVAL_MS = 1000;
	private static final int MAX_LINES_PER_BATCH = 80;
	private static final int MAX_CHARS_PER_BATCH = 6000;

	// Upper bound of the reconnect buffer. Reaching it means the client has been disconnected long enough
	// for the oldest lines to be worthless anyway, so they are dropped instead of growing the buffer forever.
	private static final int MAX_PENDING_LINES = 10_000;

	private static final Path LATEST_LOG_PATH = Path.of("logs", "latest.log");

	private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
	private static final ArrayDeque<String> pendingLines = new ArrayDeque<>();
	private static ScheduledExecutorService executor;
	private static Object currentFileKey;
	private static long pointer;

	// True once the current overflow episode has been reported, so a long disconnect logs the dropped lines
	// only once instead of once per line or once per poll.
	private static boolean pendingOverflowNotified;

	private ConsoleLogTailer() {
	}

	static synchronized void updateEnabled(boolean enabled) {
		boolean wasEnabled = ENABLED.getAndSet(enabled);
		if (enabled) {
			startIfNeeded();
		} else if (wasEnabled) {
			stop();
		}
	}

	static synchronized void stop() {
		ENABLED.set(false);
		resetFileTracking(true);
		pendingLines.clear();
		pendingOverflowNotified = false;
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private static synchronized void startIfNeeded() {
		if (executor != null && !executor.isShutdown()) {
			return;
		}
		executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "DMCC-ConsoleLogTailer"));
		executor.scheduleWithFixedDelay(ConsoleLogTailer::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private static synchronized void poll() {
		if (!ENABLED.get()) {
			return;
		}

		try {
			if (!Files.exists(LATEST_LOG_PATH)) {
				// latest.log can be absent briefly during rollover; wait for recreation.
				resetFileTracking(false);
				return;
			}

			Object latestKey = readFileKey();
			long latestLength = Files.size(LATEST_LOG_PATH);

			boolean replaced = currentFileKey != null && latestKey != null && !currentFileKey.equals(latestKey);
			boolean truncated = latestLength < pointer;
			if (replaced || truncated) {
				pointer = 0;
			}

			readAvailableLinesIntoBuffer();
			currentFileKey = latestKey;

			flushPendingBatchesIfConnected();
		} catch (Exception e) {
			LOGGER.warn(I18nManager.getDmccTranslation("client.console_log_tailer.poll_failed", e.getMessage()));
			resetFileTracking(false);
		}
	}

	private static void readAvailableLinesIntoBuffer() throws Exception {
		try (RandomAccessFile localReader = new RandomAccessFile(LATEST_LOG_PATH.toFile(), "r")) {
			long length = localReader.length();
			if (pointer > length) {
				pointer = 0;
			}

			localReader.seek(pointer);
			String line;
			while ((line = localReader.readLine()) != null) {
				String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
				if (!utf8.isBlank()) {
					appendPendingLine(normalizeLine(utf8));
				}
			}
			pointer = localReader.getFilePointer();
		}
	}

	/**
	 * Appends one line to the reconnect buffer, keeping that buffer bounded.
	 * <p>
	 * Once {@value #MAX_PENDING_LINES} lines are buffered, the oldest one is dropped for every new line:
	 * after a long disconnect the newest lines are the interesting ones. The drop is reported once per
	 * overflow episode through {@code client.console_log_tailer.pending_lines_dropped}.
	 *
	 * @param line The normalized console line to buffer.
	 */
	private static void appendPendingLine(String line) {
		if (pendingLines.size() >= MAX_PENDING_LINES) {
			pendingLines.removeFirst();
			if (!pendingOverflowNotified) {
				pendingOverflowNotified = true;
				LOGGER.warn(I18nManager.getDmccTranslation("client.console_log_tailer.pending_lines_dropped", MAX_PENDING_LINES));
			}
		}
		pendingLines.addLast(line);
	}

	private static void flushPendingBatchesIfConnected() {
		if (pendingLines.isEmpty() || !isClientConnected()) {
			return;
		}

		while (!pendingLines.isEmpty()) {
			List<String> batch = collectNextBatch();
			if (batch.isEmpty()) {
				return;
			}

			// Re-check right before handing the batch over: nothing has been removed from the buffer yet, so a
			// connection that dropped while the batch was assembled simply leaves the lines buffered.
			if (!isClientConnected()) {
				return;
			}

			for (int i = 0; i < batch.size(); i++) {
				pendingLines.removeFirst();
			}

			try {
				NetworkManager.sendPacketToServer(new Packets.ConsoleLogBatch(List.copyOf(batch)));
			} catch (Exception e) {
				// The batch never reached the wire: put it back in the original order so the next poll retries
				// it instead of silently dropping the whole batch.
				for (int i = batch.size() - 1; i >= 0; i--) {
					pendingLines.addFirst(batch.get(i));
				}
				LOGGER.warn(I18nManager.getDmccTranslation("client.console_log_tailer.flush_failed", e.getMessage()));
				return;
			}
		}

		// The backlog is drained, so the next overflow reports itself again.
		pendingOverflowNotified = false;
	}

	/**
	 * Collects the next batch from the head of the buffer without removing anything.
	 *
	 * @return The lines to send next, in buffer order.
	 */
	private static List<String> collectNextBatch() {
		List<String> batch = new ArrayList<>();
		int chars = 0;
		for (String line : pendingLines) {
			int nextChars = chars + line.length() + 1;
			if (!batch.isEmpty() && (batch.size() >= MAX_LINES_PER_BATCH || nextChars > MAX_CHARS_PER_BATCH)) {
				break;
			}
			batch.add(line);
			chars = nextChars;
		}
		return batch;
	}

	private static String normalizeLine(String line) {
		if (line == null) {
			return "";
		}
		return line.length() > MAX_CHARS_PER_BATCH ? line.substring(0, MAX_CHARS_PER_BATCH) : line;
	}

	private static boolean isClientConnected() {
		ClientDMCC client = NetworkManager.getClient();
		return client != null && client.isConnected();
	}

	private static Object readFileKey() {
		try {
			BasicFileAttributes attributes = Files.readAttributes(LATEST_LOG_PATH, BasicFileAttributes.class);
			return attributes.fileKey();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void resetFileTracking(boolean resetPointer) {
		currentFileKey = null;
		if (resetPointer) {
			pointer = 0;
		}
	}
}
