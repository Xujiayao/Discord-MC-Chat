package com.xujiayao.discord_mc_chat.minecraft.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.HttpUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Minecraft translations for official, mods and datapacks.
 *
 * @author Xujiayao
 */
public class TranslationManager {

	private static final Map<String, String> TRANSLATIONS = new HashMap<>();
	private static final Path CACHE_DIR = Paths.get("./config/discord_mc_chat/cache/lang");

	private static String currentLoadedLanguage = "";

	/**
	 * Initializes the translation manager.
	 * <p>
	 * Loads translations for the current target language first, then falls back to en_us.
	 */
	public static void init() {
		TRANSLATIONS.clear();

		String language = I18nManager.getLanguage();

		// Load the target language translations
		loadTranslations(language);
		int loadedCount = TRANSLATIONS.size();

		// Load en_us translations to fill in missing keys (fallback)
		if (!"en_us".equals(language)) {
			loadTranslations("en_us");
			LOGGER.info("Loaded {}/{} Minecraft \"{}\" translations", loadedCount, TRANSLATIONS.size(), language);
		}

		currentLoadedLanguage = language;
	}

	/**
	 * Gets a Minecraft translation with the specified key and arguments.
	 * <p>
	 * This method handles Minecraft's placeholder format (%s, %1$s, %2$s, etc.)
	 *
	 * @param key  The translation key
	 * @param args The arguments to format into the string
	 * @return The translated and formatted string, or the key if not found
	 */
	public static String get(String key, Object... args) {
		if (!currentLoadedLanguage.equals(I18nManager.getLanguage())) {
			init();
		}

		String translation = TRANSLATIONS.get(key);

		if (translation == null) {
			LOGGER.warn("Translation not found for key: '{}', returning key as-is.", key);
			return key;
		}

		if (args == null || args.length == 0) {
			return translation;
		}

		// Handle Minecraft's placeholder format
		return StringUtils.format(translation, args);
	}

//	/**
//	 * Loads translations from a PackRepository (for datapacks).
//	 *
//	 * @param packRepository The pack repository to load from
//	 */
//	public static void loadFromPackRepository(PackRepository packRepository) {
//		LOGGER.info("[MTM] loadFromPackRepository() called");
//
//		if (packRepository == null) {
//			LOGGER.warn("[MTM] PackRepository is null, skipping");
//			return;
//		}
//
//		int packCount = 0;
//		for (Pack pack : packRepository.getSelectedPacks()) {
//			packCount++;
//			LOGGER.info("[MTM] Processing pack: {}", pack.getId());
//			try {
//				pack.streamSelfAndChildren().forEach(packResources -> {
//					LOGGER.info("[MTM]     Processing pack resources: {}", packResources.getId());
//
//					packResources.open().getNamespaces(PackType.CLIENT_RESOURCES).forEach(namespace -> {
//						LOGGER.info("[MTM]       Checking namespace: {}", namespace);
//
//						IoSupplier<InputStream> supplier = packResources.open().getResource(
//								PackType.CLIENT_RESOURCES,
//								net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "lang/" + language + ".json")
//						);
//
//						if (supplier != null) {
//							LOGGER.info("[MTM]         Found lang resource for namespace '{}'", namespace);
//							try (InputStream is = supplier.get()) {
//								Map<String, String> translations = JSON_MAPPER.readValue(is,
//										new TypeReference<Map<String, String>>() {
//										});
//								int count = translations.size();
//								TRANSLATIONS.putAll(translations);
//								LOGGER.info("[MTM]         Loaded {} translations", count);
//							} catch (IOException e) {
//								LOGGER.warn("[MTM]         Failed to load: {}", e.getMessage());
//							}
//						} else {
//							LOGGER.info("[MTM]         No lang resource found for namespace '{}'", namespace);
//						}
//					});
//				});
//			} catch (Exception e) {
//				LOGGER.warn("[MTM] Failed to load translations from pack: {}", pack.getId());
//				LOGGER.warn("[MTM] Exception: {}", e.getMessage());
//			}
//		}
//		LOGGER.info("[MTM] Processed {} packs from PackRepository", packCount);
//	}

	/**
	 * Loads translations for a specific language.
	 *
	 * @param language The language code
	 */
	private static void loadTranslations(String language) {
		// Step 1: Official Minecraft translations
		try {
			String version = EnvironmentUtils.getMinecraftVersion();
			String fileName = StringUtils.format("{}-{}.json", language, version);

			Files.createDirectories(CACHE_DIR);
			Path langCachePath = CACHE_DIR.resolve(fileName);

			boolean loaded = false;
			// If a valid cached file exists, use it.
			if (Files.exists(langCachePath)) {
				try {
					JsonNode root = JSON_MAPPER.readTree(Files.newBufferedReader(langCachePath, StandardCharsets.UTF_8));
					Map<String, String> translations = JSON_MAPPER.convertValue(root, new TypeReference<>() {
					});
					translations.forEach(TRANSLATIONS::putIfAbsent);

					LOGGER.info("Loaded Minecraft translations from cache for version {}", version);
					loaded = true;
				} catch (Exception e) {
					LOGGER.error("Failed to read cached Minecraft translations, will attempt to re-download", e);
					Files.delete(langCachePath);
				}
			}

			if (!loaded) {
				// Otherwise, download the file.
				LOGGER.info("Downloading Minecraft translations for version {}...", version);
				String url = "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + version + "/assets/minecraft/lang/" + language + ".json";

				try {
					String jsonContent = HttpUtils.get(url);
					Files.writeString(langCachePath, jsonContent);

					JsonNode root = JSON_MAPPER.readTree(jsonContent);
					Map<String, String> translations = JSON_MAPPER.convertValue(root, new TypeReference<>() {
					});
					translations.forEach(TRANSLATIONS::putIfAbsent);

					LOGGER.info("Downloaded and cached Minecraft translations, file size: {} bytes", jsonContent.length());
				} catch (Exception e) {
					LOGGER.error("Failed to download or cache Minecraft translations for version " + version, e);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Unexpected error while loading official Minecraft translations", e);
		}

		// Step 2: Scan mods directory
		Path modsDir = Path.of("./mods");
		if (Files.exists(modsDir) && Files.isDirectory(modsDir)) {
			try (Stream<Path> modFiles = Files.list(modsDir)) {
				modFiles.filter(p -> p.toString().endsWith(".jar")).forEach(jarPath -> {
					try (FileSystem fileSystem = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
						Path assetsRoot = fileSystem.getPath("assets");

						if (!Files.exists(assetsRoot)) {
							return;
						}

						try (Stream<Path> namespaces = Files.list(assetsRoot)) {
							namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
								Path langFile = namespaceDir.resolve("lang").resolve(language + ".json");

								if (!Files.exists(langFile)) {
									return;
								}

								try (InputStream is = Files.newInputStream(langFile)) {
									Map<String, String> translations = JSON_MAPPER.readValue(is, new TypeReference<>() {
									});
									translations.forEach(TRANSLATIONS::putIfAbsent);
								} catch (Exception e) {
									LOGGER.error("Failed to load translations from mod JAR", e);
								}
							});
						}
					} catch (Exception e) {
						LOGGER.error("Cannot open JAR file", e);
					}
				});
			} catch (Exception e) {
				LOGGER.error("Error scanning mods directory.", e);
			}
		}
	}
}
