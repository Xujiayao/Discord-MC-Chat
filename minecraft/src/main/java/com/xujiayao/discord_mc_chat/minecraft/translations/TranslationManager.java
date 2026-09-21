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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Minecraft translations for official, mods and datapacks.
 */
public final class TranslationManager {

	/**
	 * Published in a single volatile write, so concurrent readers only ever observe a complete snapshot
	 * instead of a half-loaded map; the map is never mutated afterwards, making it safe to read anywhere.
	 */
	private static volatile Map<String, String> translations = Map.of();
	private static final Path CACHE_DIR = Path.of("./config/discord_mc_chat/cache/lang");

	private static volatile String currentLoadedLanguage = "";
	private static volatile MinecraftServer server;

	/**
	 * Missing keys already warned about for the currently published snapshot, cleared right before a new
	 * snapshot is published, so a missing key is warned about at most once per load.
	 */
	private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

	/**
	 * Thread currently executing {@link #init()}. The load runs directly on the calling thread now, so a
	 * nested call (for example through {@link #get(String, Object...)}) would recurse forever.
	 */
	private static volatile Thread loadingThread;

	private TranslationManager() {
	}

	public static void setServer(MinecraftServer server) {
		TranslationManager.server = server;
	}

	/**
	 * Loads translations for the current target language first, then falls back to en_us.
	 */
	public static void init() {
		Thread current = Thread.currentThread();
		if (loadingThread == current) {
			// Re-entrant call from inside the running load: the outer call publishes the result.
			return;
		}

		synchronized (TranslationManager.class) {
			loadingThread = current;
			try {
				loadAll();
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.init_failed"), e);
			} finally {
				loadingThread = null;
			}
		}
	}

	private static void loadAll() {
		if (server == null) {
			// Called before ServerStarted event (no MinecraftServer yet); init() runs again on the first get()
			return;
		}

		Map<String, String> loaded = new HashMap<>();

		String language = I18nManager.getLanguage();

		loadTranslations(language, loaded);
		int loadedCount = loaded.size();

		// Load en_us translations to fill in missing keys (fallback)
		if (!"en_us".equals(language)) {
			loadTranslations("en_us", loaded);
		}

		// A new snapshot gets a fresh warn-once bookkeeping.
		WARNED_KEYS.clear();
		translations = loaded;

		LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.loaded", loadedCount, loaded.size(), language));
		currentLoadedLanguage = language;
	}

	/**
	 * @return The translated and formatted string, or the key if not found
	 */
	public static String get(String key, Object... args) {
		ensureTranslationsLoaded();

		String translation = translations.get(key);

		if (translation == null) {
			// Warn at most once per key for the currently published snapshot.
			if (WARNED_KEYS.add(key)) {
				LOGGER.warn(I18nManager.getDmccTranslation("minecraft.translations.key_not_found", key));
			}
			return key;
		}

		if (args == null || args.length == 0) {
			return translation;
		}

		// Handle Minecraft's placeholder format
		return StringUtils.format(translation, args);
	}

	/**
	 * Resolves {@code TranslatableContents} against the configured language.
	 */
	public static String get(Component component) {
		ensureTranslationsLoaded();

		if (component == null) {
			return "";
		}

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

		return component.getString();
	}

	private static void loadTranslations(String language, Map<String, String> target) {
		// Step 1: Official Minecraft translations
		try {
			String version = EnvironmentUtils.getMinecraftVersion();
			String fileName = StringUtils.format("{}-{}.json", language, version);

			Files.createDirectories(CACHE_DIR);
			Path langCachePath = CACHE_DIR.resolve(fileName);

			boolean loaded = false;
			if (Files.exists(langCachePath)) {
				try {
					Map<String, String> entries = JsonUtils.toStringMap(Files.newBufferedReader(langCachePath, StandardCharsets.UTF_8));
					entries.forEach(target::putIfAbsent);

					LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.cache_loaded", language, version));
					loaded = true;
				} catch (Exception e) {
					LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.cache_read_failed"), e);
					Files.delete(langCachePath);
				}
			}

			if (!loaded) {
				LOGGER.info(I18nManager.getDmccTranslation("minecraft.translations.downloading", language, version));
				String url = "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + version + "/assets/minecraft/lang/" + language + ".json";

				try {
					String jsonContent = HttpUtils.get(url);
					Files.writeString(langCachePath, jsonContent);

					Map<String, String> entries = JsonUtils.toStringMap(jsonContent);
					entries.forEach(target::putIfAbsent);

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
									Map<String, String> entries = JsonUtils.toStringMap(is);
									entries.forEach(target::putIfAbsent);
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
			pack.open().forEach(packResources -> {
				try (PackResources resources = packResources) {
					resources.getNamespaces(PackType.CLIENT_RESOURCES).forEach(namespace -> {
						IoSupplier<InputStream> supplier = resources.getResource(
								PackType.CLIENT_RESOURCES,
								Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json")
						);

						if (supplier != null) {
							try (InputStream is = supplier.get()) {
								Map<String, String> entries = JsonUtils.toStringMap(is);
								entries.forEach(target::putIfAbsent);
							} catch (Exception e) {
								LOGGER.error(I18nManager.getDmccTranslation("minecraft.translations.datapack_load_failed"), e);
							}
						}
					});
				}
			});
		}
	}

	private static void ensureTranslationsLoaded() {
		if (server == null) {
			// Called before ServerStarted event (no MinecraftServer yet): init() would only return, so
			// skipping keeps the same observable output without rebuilding the load machinery per get().
			return;
		}

		if (!currentLoadedLanguage.equals(I18nManager.getLanguage())) {
			init();
		}
	}
}
