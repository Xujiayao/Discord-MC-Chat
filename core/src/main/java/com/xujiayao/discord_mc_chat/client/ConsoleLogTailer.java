package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.ConsoleLogBatchPacket;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

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
 * </ul>
 *
 * @author Xujiayao
 */
final class ConsoleLogTailer {

	private static final long POLL_INTERVAL_MS = 1000;
	private static final int MAX_LINES_PER_BATCH = 80;
	private static final int MAX_CHARS_PER_BATCH = 6000;
	private static final Path LATEST_LOG_PATH = Path.of("logs", "latest.log");

	private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
	private static final ArrayDeque<String> pendingLines = new ArrayDeque<>();
	private static ScheduledExecutorService executor;
	private static Object currentFileKey;
	private static long pointer;

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
					pendingLines.addLast(normalizeLine(utf8));
				}
			}
			pointer = localReader.getFilePointer();
		}
	}

	private static void flushPendingBatchesIfConnected() {
		if (pendingLines.isEmpty() || !isClientConnected()) {
			return;
		}

		while (!pendingLines.isEmpty() && isClientConnected()) {
			List<String> batch = new ArrayList<>();
			int chars = 0;
			while (!pendingLines.isEmpty()) {
				String line = pendingLines.peekFirst();
				int nextChars = chars + line.length() + 1;
				if (!batch.isEmpty() && (batch.size() >= MAX_LINES_PER_BATCH || nextChars > MAX_CHARS_PER_BATCH)) {
					break;
				}
				batch.add(pendingLines.removeFirst());
				chars = nextChars;
			}
			if (!batch.isEmpty()) {
				NetworkManager.sendPacketToServer(new ConsoleLogBatchPacket(List.copyOf(batch)));
			}
		}
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
