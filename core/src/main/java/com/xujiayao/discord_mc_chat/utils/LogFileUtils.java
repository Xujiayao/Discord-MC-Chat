package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

	private LogFileUtils() {
	}

	/**
	 * Lists available log files in the given directory.
	 *
	 * @return A list of log file names
	 */
	public static List<String> listLogFiles() {
		List<String> files = new ArrayList<>();
		Path dir = Paths.get(LOGS_DIR);

		if (!Files.exists(dir) || !Files.isDirectory(dir)) {
			return files;
		}

		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString();
						return name.endsWith(".log") || name.endsWith(".log.gz");
					})
					.sorted((p1, p2) -> p2.getFileName().toString().compareTo(p1.getFileName().toString()))
					.forEach(p -> files.add(p.getFileName().toString()));
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.log.list_failed"), e);
		}

		return files;
	}

	/**
	 * Reads a log file, decompressing .gz files if necessary.
	 *
	 * @param fileName The file name to read
	 * @return The file content as bytes, or null if the file does not exist
	 */
	public static byte[] readLogFile(String fileName) {
		Path filePath = Paths.get(LOGS_DIR).resolve(fileName).normalize();

		if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
			return null;
		}

		try {
			if (fileName.endsWith(".gz")) {
				// Decompress gzip file
				try (InputStream fis = Files.newInputStream(filePath);
				     GZIPInputStream gzis = new GZIPInputStream(fis);
				     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					byte[] buffer = new byte[8192];
					int len;
					while ((len = gzis.read(buffer)) != -1) {
						baos.write(buffer, 0, len);
					}
					return baos.toByteArray();
				}
			} else {
				return Files.readAllBytes(filePath);
			}
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.log.read_failed", fileName), e);
			return null;
		}
	}
}
