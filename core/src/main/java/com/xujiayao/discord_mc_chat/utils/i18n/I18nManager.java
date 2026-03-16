package com.xujiayao.discord_mc_chat.utils.i18n;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Manages internationalization (i18n) for DMCC.
 * Handles loading language files for DMCC Translations and Custom Messages.
 *
 * @author Xujiayao
 */
public class I18nManager {

	private static final Map<String, String> DMCC_TRANSLATIONS = new HashMap<>();
	private static final Path CUSTOM_MESSAGES_DIR = Paths.get("./config/discord_mc_chat/custom_messages");
	private static String language = detectLanguage();
	private static JsonNode customMessages;

	/**
	 * Gets the currently selected language code.
	 *
	 * @return The language code (e.g., "en_us").
	 */
	public static String getLanguage() {
		return language;
	}

	/**
	 * Detects the system language and checks if it is supported by DMCC.
	 *
	 * @return The detected language code (e.g., "zh_cn") if supported, otherwise "en_us".
	 */
	public static String detectLanguage() {
		String code = Locale.getDefault().toString().toLowerCase();

		// Check if the internal translation file exists for the detected language
		if (I18nManager.class.getResource("/lang/" + code + ".yml") != null) {
			return code;
		}

		return "en_us";
	}

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

	/**
	 * Loads all necessary language files based on the selected language.
	 *
	 * @param lang The language code to load (e.g., "en_us").
	 * @return true if all necessary language files were loaded successfully, false otherwise.
	 */
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

		// For client-only mode, we only need DMCC translations for logs and basic messages.
		if (!"multi_server_client".equals(ModeManager.getMode())) {
			// For server-enabled modes, load the full I18n suite.
			if (!loadCustomMessages()) {
				return false;
			}
		}

		LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.fully_loaded"));
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
				LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.language_not_supported", language));
				LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.contribute"));
				LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.contribute_link"));
				return false;
			}
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.check_failed"), e);
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
			LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.load_failed", resourcePath), e);
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

					LOGGER.warn(I18nManager.getDmccTranslation("utils.i18n.custom_not_found", language));
					LOGGER.warn(I18nManager.getDmccTranslation("utils.i18n.custom_creating", customMessagesPath));
					LOGGER.warn(I18nManager.getDmccTranslation("utils.i18n.using_default"));
					LOGGER.warn(I18nManager.getDmccTranslation("utils.i18n.custom_edit_prompt", customMessagesPath));
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
			if (!YamlUtils.validate(userMessages, templateMessages, false)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.custom_validation_failed"));
				return false;
			}

			customMessages = userMessages;
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.custom_load_failed"), e);
		}

		return false;
	}

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

	/**
	 * Gets a custom message string value by dot-separated path.
	 * <p>
	 * This method avoids exposing Jackson types, making it safe to call from
	 * the minecraft module where Jackson is not available on the classloader.
	 *
	 * @param dotPath The dot-separated path (e.g., "discord_to_minecraft.mentioned").
	 * @return The string value at the path, or an empty string if not found.
	 */
	public static String getCustomMessage(String dotPath) {
		if (customMessages == null) return "";

		String[] parts = dotPath.split("\\.");
		JsonNode node = customMessages;
		for (String part : parts) {
			node = node.path(part);
		}
		return node.asText("");
	}

	/**
	 * Gets a custom message template array by dot-separated path.
	 * <p>
	 * Returns the template as a {@code List<Map<String, Object>>} containing only
	 * standard Java types (String, Boolean), so that it can be consumed by the
	 * minecraft module without requiring Jackson on its classloader.
	 * <p>
	 * Each map in the list may contain the following keys:
	 * <ul>
	 *   <li>{@code text} (String)</li>
	 *   <li>{@code color} (String)</li>
	 *   <li>{@code bold} (Boolean)</li>
	 *   <li>{@code italic} (Boolean)</li>
	 *   <li>{@code underlined} (Boolean)</li>
	 *   <li>{@code strikethrough} (Boolean)</li>
	 * </ul>
	 *
	 * @param dotPath The dot-separated path (e.g., "discord_to_minecraft.chat").
	 * @return The template as a list of maps, or null if the path does not resolve to an array.
	 */
	public static List<Map<String, Object>> getCustomMessageTemplate(String dotPath) {
		if (customMessages == null) return null;

		String[] parts = dotPath.split("\\.");
		JsonNode node = customMessages;
		for (String part : parts) {
			node = node.path(part);
		}

		if (!node.isArray()) return null;

		List<Map<String, Object>> result = new ArrayList<>();
		for (JsonNode element : node) {
			Map<String, Object> segment = new LinkedHashMap<>();
			segment.put("text", element.path("text").asText(""));
			if (element.has("color")) segment.put("color", element.path("color").asText());
			if (element.has("bold")) segment.put("bold", element.path("bold").asBoolean(false));
			if (element.has("italic")) segment.put("italic", element.path("italic").asBoolean(false));
			if (element.has("underlined")) segment.put("underlined", element.path("underlined").asBoolean(false));
			if (element.has("strikethrough")) segment.put("strikethrough", element.path("strikethrough").asBoolean(false));
			result.add(segment);
		}
		return result;
	}

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
