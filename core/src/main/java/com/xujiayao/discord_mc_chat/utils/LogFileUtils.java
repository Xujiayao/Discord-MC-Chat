package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Utility class for reading and listing log files.
 *
 * @author Xujiayao
 */
public final class LogFileUtils {

	private static final String LOGS_DIR = "./logs";
	// Log contents are returned to the caller as a single byte[] (and, for Discord, re-uploaded as an
	// attachment), so an unbounded read of a huge file - or of a small .gz that decompresses without
	// bound - is an OOM. 8 MiB keeps a full log comfortably below Discord's default 10 MiB upload cap.
	private static final int MAX_LOG_FILE_BYTES = 8 * 1024 * 1024;

	private LogFileUtils() {
	}

	/**
	 * Lists available log files in the given directory, newest name first.
	 *
	 * @return A list of log file names
	 */
	public static List<String> listLogFiles() {
		Path dir = Paths.get(LOGS_DIR);

		if (!Files.exists(dir) || !Files.isDirectory(dir)) {
			return List.of();
		}

		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString();
						return name.endsWith(".log") || name.endsWith(".log.gz");
					})
					.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
					.map(p -> p.getFileName().toString())
					.toList();
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.log.list_failed"), e);
			return List.of();
		}
	}

	/**
	 * Reads a log file, decompressing .gz files if necessary.
	 *
	 * @param fileName The file name to read
	 * @return The file content as bytes, or null if the file does not exist
	 */
	public static byte[] readLogFile(String fileName) {
		Path logsDir = Paths.get(LOGS_DIR).toAbsolutePath().normalize();
		Path filePath = logsDir.resolve(fileName).normalize();

		// The name is user input (commands/impl/LogCommand), so a path that normalizes outside the
		// logs directory ("../../config/discord_mc_chat/config.yml") must be refused instead of
		// handed back - it would expose the bot token and shared secret to the requester.
		if (!filePath.startsWith(logsDir)) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.log.read_failed", fileName));
			return null;
		}

		if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
			return null;
		}

		try {
			if (fileName.endsWith(".gz")) {
				// Decompress gzip file
				try (InputStream fis = Files.newInputStream(filePath);
				     GZIPInputStream gzis = new GZIPInputStream(fis)) {
					return readBounded(gzis, fileName);
				}
			} else {
				if (Files.size(filePath) > MAX_LOG_FILE_BYTES) {
					LOGGER.error(I18nManager.getDmccTranslation("commands.log.read_failed", fileName));
					return null;
				}
				return Files.readAllBytes(filePath);
			}
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.log.read_failed", fileName), e);
			return null;
		}
	}

	/**
	 * Reads a stream into memory, refusing to grow beyond {@link #MAX_LOG_FILE_BYTES}.
	 *
	 * @return The content, or null when the limit is exceeded
	 */
	private static byte[] readBounded(InputStream in, String fileName) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			// The compressed size says nothing about the decompressed size, so the limit is enforced
			// while reading rather than by checking the file size up front.
			if (out.size() + read > MAX_LOG_FILE_BYTES) {
				LOGGER.error(I18nManager.getDmccTranslation("commands.log.read_failed", fileName));
				return null;
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}
}
