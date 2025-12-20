package com.xujiayao.discord_mc_chat.utils.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Manages internationalization (i18n) for DMCC.
 * Handles loading language files for custom messages, DMCC translations, and Minecraft translations.
 *
 * @author Xujiayao
 */
public class I18nManager {

	private static String language = "en_us";

	private static final Map<String, String> DMCC_TRANSLATIONS = new HashMap<>();

	private static final Path CUSTOM_MESSAGES_DIR = Paths.get("./config/discord_mc_chat/custom_messages");
	private static JsonNode customMessages;

	private static final Path CACHE_DIR = Paths.get("./config/discord_mc_chat/cache/lang");
	private static final Map<String, String> minecraftTranslations = new HashMap<>();

	/**
	 * Loads only DMCC's internal translations from resources.
	 *
	 * @return true if DMCC translations were loaded successfully, false otherwise.
	 */
	public static boolean loadInternalTranslationsOnly() {
		if (DMCC_TRANSLATIONS.isEmpty()) {
			// Check if required resource files exist for the selected language
			if (!checkLanguageResources()) {
				return false;
			}

			// Load DMCC internal translations
			return loadDmccTranslations();
		}
		return true;
	}


	public static boolean load(String lang) {
		language = lang;

		// Check if required resource files exist for the selected language
		if (!checkLanguageResources()) {
			return false;
		}

		// Load DMCC internal translations
		if (!loadDmccTranslations()) {
			return false;
		}

		if ("multi_server_client".equals(ModeManager.getMode())) {
			// For client-only mode, we only need DMCC translations for logs and basic messages.
			LOGGER.info("DMCC internal translations for \"{}\" loaded successfully!", language);
		} else {
			// For server-enabled modes, load the full I18n suite.
			if (!loadCustomMessages()) {
				return false;
			}

			// Load official Minecraft translations, from cache or by downloading
			//		if (!loadMinecraftTranslations()) {
			//			return false;
			//		}

			LOGGER.info("All language files for \"{}\" loaded successfully!", language);
		}
		return true;
	}

	/**
	 * Checks if the necessary language files exist as resources within the JAR.
	 *
	 * @return true if all required language resources are found.
	 */
	private static boolean checkLanguageResources() {
		try (InputStream customMessagesStream = I18nManager.class.getResourceAsStream("/config/custom_messages/" + language + ".yml");
			 InputStream dmccLangStream = I18nManager.class.getResourceAsStream("/lang/" + language + ".yml")) {

			if (customMessagesStream == null || dmccLangStream == null) {
				LOGGER.error("Language \"{}\" is not supported", language);
				LOGGER.info("You are welcome to contribute translations to DMCC!");
				LOGGER.info("For more details, see: https://github.com/Xujiayao/Discord-MC-Chat#Contributing");
				return false;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to check language resources", e);
			return false;
		}
		return true;
	}

	/**
	 * Loads DMCC's internal translation file from resources.
	 *
	 * @return true if the translations were loaded successfully.
	 */
	private static boolean loadDmccTranslations() {
		DMCC_TRANSLATIONS.clear();
		String resourcePath = "/lang/" + language + ".yml";

		try (InputStream inputStream = I18nManager.class.getResourceAsStream(resourcePath)) {
			JsonNode rootNode = YAML_MAPPER.readTree(inputStream);
			flattenJsonToMap(rootNode, "", DMCC_TRANSLATIONS);
		} catch (IOException e) {
			LOGGER.error("Failed to load DMCC translations from " + resourcePath, e);
			return false;
		}

		return true;
	}

	/**
	 * Loads the custom messages configuration file.
	 *
	 * @return true if the custom messages were loaded and validated successfully.
	 */
	private static boolean loadCustomMessages() {
		try {
			Files.createDirectories(CUSTOM_MESSAGES_DIR);
			Path customMessagesPath = CUSTOM_MESSAGES_DIR.resolve(language + ".yml");
			String templatePath = "/config/custom_messages/" + language + ".yml";

			// If the custom messages file does not exist or is empty, copy the template.
			if (!Files.exists(customMessagesPath) || Files.size(customMessagesPath) == 0) {
				try (InputStream inputStream = I18nManager.class.getResourceAsStream(templatePath)) {
					if (inputStream == null) {
						throw new IOException("Default custom messages template not found: " + templatePath);
					}
					Files.copy(inputStream, customMessagesPath, StandardCopyOption.REPLACE_EXISTING);

					LOGGER.warn("Custom messages file for \"{}\" not found or is empty", language);
					LOGGER.warn("Creating a default one at \"{}\"", customMessagesPath);
					LOGGER.warn("DMCC will continue using default values");
					LOGGER.warn("If you wish to customize messages, please edit \"{}\" and reload DMCC.", customMessagesPath);
				}
			}

			// Load the user's custom messages file.
			JsonNode userMessages = YAML_MAPPER.readTree(Files.newBufferedReader(customMessagesPath, StandardCharsets.UTF_8));

			// Load the template for validation.
			JsonNode templateMessages;
			try (InputStream templateStream = I18nManager.class.getResourceAsStream(templatePath)) {
				templateMessages = YAML_MAPPER.readTree(templateStream);
			}

			// Validate the user's file against the template.
			// The `errorOnUnmodified` flag is set to false because users might not need to customize messages.
			if (!YamlUtils.validate(userMessages, templateMessages, customMessagesPath, false)) {
				LOGGER.error("Validation of custom message config failed");
				return false;
			}

			customMessages = userMessages;
			LOGGER.info("Custom messages for \"{}\" loaded successfully!", language);
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to load custom messages", e);
		}

		return false;
	}

//	/**
//	 * Loads the official Minecraft translation file for the current language.
//	 * It attempts to use a cached version before downloading a new one.
//	 *
//	 * @return true if the translations were loaded successfully.
//	 */
//	private static boolean loadMinecraftTranslations() {
//		minecraftTranslations.clear();
//
//		try {
//			String mcVersion = "EnvironmentUtils.getMinecraftVersion()";
//			String fileName = StringUtils.format("{}-{}.json", language, mcVersion);
//
//			Files.createDirectories(CACHE_DIR);
//			Path langCachePath = CACHE_DIR.resolve(fileName);
//
//			// If a valid cached file exists, use it.
//			if (Files.exists(langCachePath)) {
//				try {
//					JsonNode root = JSON_MAPPER.readTree(Files.newBufferedReader(langCachePath, StandardCharsets.UTF_8));
//					minecraftTranslations.putAll(JSON_MAPPER.convertValue(root, new TypeReference<Map<String, String>>() {
//					}));
//
//					LOGGER.info("Loaded Minecraft translations from cache for version {}", mcVersion);
//					return true;
//				} catch (Exception e) {
//					LOGGER.error("Failed to read cached Minecraft translations, will attempt to re-download", e);
//				}
//			}
//
//			// Otherwise, download the file.
//			LOGGER.info("Downloading Minecraft translations for version {}...", mcVersion);
//			String url = "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + mcVersion + "/assets/minecraft/lang/" + language + ".json";
//			Request request = new Request.Builder().url(url).build();
//
//			try (Response response = OK_HTTP_CLIENT.newCall(request).execute()) {
//				if (!response.isSuccessful()) {
//					LOGGER.error("Failed to download Minecraft translations. HTTP Status: {}", response.code());
//					return false;
//				}
//
//				String jsonContent = response.body().string();
//				Files.writeString(langCachePath, jsonContent);
//
//				JsonNode root = JSON_MAPPER.readTree(jsonContent);
//				minecraftTranslations.putAll(JSON_MAPPER.convertValue(root, new TypeReference<Map<String, String>>() {
//				}));
//
//				LOGGER.info("Downloaded and cached Minecraft translations, file size: {} bytes", jsonContent.length());
//				return true;
//			}
//		} catch (IOException e) {
//			LOGGER.error("Failed to load or download Minecraft translations", e);
//		}
//
//		return false;
//	}

	/**
	 * Gets a translation from DMCC's internal translation files (lang/*.yml).
	 * Placeholders are formatted using {}.
	 *
	 * @param key  The translation key (e.g., "whitelist.success").
	 * @param args The arguments to format into the string.
	 * @return The formatted translation string, or the key if not found.
	 */
	public static String getDmccTranslation(String key, Object... args) {
		String translation = DMCC_TRANSLATIONS.getOrDefault(key, key);
		return StringUtils.format(translation, args);
	}

	/**
	 * Gets the custom messages JsonNode.
	 *
	 * @return The root JsonNode for custom messages.
	 */
	public static JsonNode getCustomMessages() {
		return customMessages;
	}

//	/**
//	 * Gets a translation from the official Minecraft translation files.
//	 * Placeholders are formatted using %s or %1$s.
//	 *
//	 * @param key  The translation key (e.g., "death.attack.drown").
//	 * @param args The arguments to format into the string.
//	 * @return The formatted translation string, or the key if not found.
//	 */
//	public static String getMinecraftTranslation(String key, Object... args) {
//		String translation = minecraftTranslations.getOrDefault(key, key);
//		try {
//			// MessageFormat handles both %s and %1$s style placeholders
//			return MessageFormat.format(translation.replace("'", "''"), args);
//		} catch (IllegalArgumentException e) {
//			LOGGER.warn("Failed to format Minecraft translation for key \"{}\": {}", key, e.getMessage());
//			return translation; // Return unformatted string on error
//		}
//	}

	/**
	 * Recursively flattens a JsonNode object into a Map with dot-separated keys.
	 *
	 * @param node The JsonNode to flatten.
	 * @param path The current path prefix.
	 * @param map  The map to store the flattened key-value pairs.
	 */
	private static void flattenJsonToMap(JsonNode node, String path, Map<String, String> map) {
		if (node.isObject()) {
			String prefix = path.isEmpty() ? "" : path + ".";
			for (Map.Entry<String, JsonNode> field : node.properties()) {
				flattenJsonToMap(field.getValue(), prefix + field.getKey(), map);
			}
		} else if (node.isTextual()) {
			map.put(path, node.asText());
		}
	}
}
