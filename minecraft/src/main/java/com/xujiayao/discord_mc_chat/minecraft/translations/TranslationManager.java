package com.xujiayao.discord_mc_chat.minecraft.translations;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.HttpUtils;
import com.xujiayao.discord_mc_chat.utils.JsonUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Minecraft translations for official, mods and datapacks.
 *
 * @author Xujiayao
 */
public final class TranslationManager {

	private static final Map<String, String> TRANSLATIONS = new HashMap<>();
	private static final Path CACHE_DIR = Paths.get("./config/discord_mc_chat/cache/lang");

	private static String currentLoadedLanguage = "";
	private static MinecraftServer server;

	private TranslationManager() {
	}

	/**
	 * Sets the Minecraft server instance.
	 *
	 * @param server The Minecraft server
	 */
	public static void setServer(MinecraftServer server) {
		TranslationManager.server = server;
	}

	/**
	 * Initializes the translation manager.
	 * <p>
	 * Loads translations for the current target language first, then falls back to en_us.
	 */
	public static void init() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Translations"))) {
			executor.submit(() -> {
				if (server == null) {
					// Called before ServerStarted event (before MinecraftServer is available)
					// Will be called again when the first get() is requested
					return;
				}

				TRANSLATIONS.clear();

				String language = I18nManager.getLanguage();

				// Load the target language translations
				loadTranslations(language);
				int loadedCount = TRANSLATIONS.size();

				// Load en_us translations to fill in missing keys (fallback)
				if (!"en_us".equals(language)) {
					loadTranslations("en_us");
				}

				LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.loaded", loadedCount, TRANSLATIONS.size(), language));
				currentLoadedLanguage = language;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.init_failed"), e);
		}
	}

	/**
	 * Gets a Minecraft translation with the specified key and arguments.
	 *
	 * @param key  The translation key
	 * @param args The arguments to format into the string
	 * @return The translated and formatted string, or the key if not found
	 */
	public static String get(String key, Object... args) {
		ensureTranslationsLoaded();

		String translation = TRANSLATIONS.get(key);

		if (translation == null) {
			LOGGER.warn(I18nManager.getDmccTranslation("minecraft.translations.key_not_found", key));
			return key;
		}

		if (args == null || args.length == 0) {
			return translation;
		}

		// Handle Minecraft's placeholder format
		return StringUtils.format(translation, args);
	}

	/**
	 * Gets the translated string from a Minecraft Component.
	 * <p>
	 * This method handles TranslatableContents to get the translation in the configured language.
	 *
	 * @param component The component to translate
	 * @return The translated string
	 */
	public static String get(Component component) {
		ensureTranslationsLoaded();

		if (component == null) {
			return "";
		}

		// Check if this is a translatable component
		if (component.getContents() instanceof TranslatableContents translatable) {
			String key = translatable.getKey();
			Object[] args = translatable.getArgs();

			// Convert Component arguments to translated strings recursively
			Object[] translatedArgs = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Component argComponent) {
					translatedArgs[i] = get(argComponent);
				} else {
					translatedArgs[i] = args[i];
				}
			}

			return get(key, translatedArgs);
		}

		// For non-translatable components, just return the string representation
		return component.getString();
	}

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
					Map<String, String> translations = JsonUtils.toStringMap(Files.newBufferedReader(langCachePath, StandardCharsets.UTF_8));
					translations.forEach(TRANSLATIONS::putIfAbsent);

					LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.cache_loaded", language, version));
					loaded = true;
				} catch (Exception e) {
					LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.cache_read_failed"), e);
					Files.delete(langCachePath);
				}
			}

			if (!loaded) {
				// Otherwise, download the file.
				LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.downloading", language, version));
				String url = "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + version + "/assets/minecraft/lang/" + language + ".json";

				try {
					String jsonContent = HttpUtils.get(url);
					Files.writeString(langCachePath, jsonContent);

					Map<String, String> translations = JsonUtils.toStringMap(jsonContent);
					translations.forEach(TRANSLATIONS::putIfAbsent);

					LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.downloaded", language, jsonContent.length()));
				} catch (Exception e) {
					LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.download_failed", language, version), e);
				}
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.official_load_failed"), e);
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
									Map<String, String> translations = JsonUtils.toStringMap(is);
									translations.forEach(TRANSLATIONS::putIfAbsent);
								} catch (Exception e) {
									LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.mod_load_failed"), e);
								}
							});
						}
					} catch (Exception e) {
						LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.jar_open_failed"), e);
					}
				});
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.mods_scan_failed"), e);
			}
		}

		// Step 3: Scan datapacks directory
		for (Pack pack : server.getPackRepository().getSelectedPacks()) {
			try (PackResources packResources = pack.open()) {
				packResources.getNamespaces(PackType.CLIENT_RESOURCES).forEach(namespace -> {
					IoSupplier<InputStream> supplier = packResources.getResource(
							PackType.CLIENT_RESOURCES,
							Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json")
					);

					if (supplier != null) {
						try (InputStream is = supplier.get()) {
							Map<String, String> translations = JsonUtils.toStringMap(is);
							translations.forEach(TRANSLATIONS::putIfAbsent);
						} catch (Exception e) {
							LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.datapack_load_failed"), e);
						}
					}
				});
			}
		}
	}

	private static void ensureTranslationsLoaded() {
		if (!currentLoadedLanguage.equals(I18nManager.getLanguage())) {
			init();
		}
	}
}
