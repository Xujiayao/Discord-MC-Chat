package com.xujiayao.discord_mc_chat.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads Minecraft language JSON files from translation archives (mod JARs and resource packs).
 *
 * @author Xujiayao
 */
public final class TranslationFileUtils {

	private TranslationFileUtils() {
	}

	/**
	 * @return whether the path looks like an archive worth scanning for translations
	 */
	public static boolean isTranslationArchive(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return Files.isRegularFile(path) && (name.endsWith(".zip") || name.endsWith(".jar"));
	}

	/**
	 * Loads `assets/<namespace>/lang/<language>.json` from a zip/jar.
	 *
	 * @return Merged translations; empty when the archive has no assets directory
	 */
	public static Map<String, String> loadFromZip(Path archive, String language) throws IOException {
		Map<String, String> translations = new LinkedHashMap<>();

		try (FileSystem fileSystem = FileSystems.newFileSystem(archive, (ClassLoader) null)) {
			Path assetsRoot = fileSystem.getPath("assets");
			if (!Files.exists(assetsRoot)) {
				return translations;
			}

			try (Stream<Path> namespaces = Files.list(assetsRoot)) {
				for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
					Path langFile = namespaceDir.resolve("lang").resolve(language + ".json");
					if (!Files.exists(langFile)) {
						continue;
					}

					try (InputStream inputStream = Files.newInputStream(langFile)) {
						JsonUtils.toStringMap(inputStream).forEach(translations::putIfAbsent);
					}
				}
			}
		}

		return translations;
	}
}
