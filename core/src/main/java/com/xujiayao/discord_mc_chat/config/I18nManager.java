package com.xujiayao.discord_mc_chat.config;

import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * @author Xujiayao
 */
public final class I18nManager {

	/**
	 * Replaced as a whole by {@link #loadDmccTranslations()} and never mutated after publication, so readers
	 * see either the previous or the fully built new map, never a half-loaded one (the previous implementation
	 * cleared and refilled a shared {@code HashMap} in place).
	 */
	private static volatile Map<String, String> dmccTranslations = new HashMap<>();

	private static final Path CUSTOM_MESSAGES_DIR = Paths.get("./config/discord_mc_chat/custom_messages");
	private static volatile String language = detectLanguage();
	private static volatile JsonNode customMessages;

	private I18nManager() {
	}

	public static String getLanguage() {
		return language;
	}

	/**
	 * @return The detected language code (e.g., "zh_cn") if supported, otherwise "en_us".
	 */
	public static String detectLanguage() {
		// Locale.ROOT: language codes are compared as plain ASCII, so the default locale must not be able to
		// change the result (the Turkish locale would turn "I" into "ı").
		String code = Locale.getDefault().toString().toLowerCase(Locale.ROOT);

		if (I18nManager.class.getResource("/lang/" + code + ".yml") != null) {
			return code;
		}

		return "en_us";
	}

	public static boolean loadInternalTranslationsOnly() {
		if (dmccTranslations.isEmpty()) {
			if (!checkLanguageResources()) {
				return false;
			}

			return loadDmccTranslations();
		}
		return true;
	}

	public static boolean load(String lang) {
		language = lang;

		if (!checkLanguageResources()) {
			return false;
		}

		if (!loadDmccTranslations()) {
			return false;
		}

		// For client-only mode, we only need DMCC translations for logs and basic messages.
		if (!"multi_server_client".equals(ModeManager.getMode())) {
			if (!loadCustomMessages()) {
				return false;
			}
		}

		LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.fully_loaded"));
		return true;
	}

	private static boolean checkLanguageResources() {
		if (I18nManager.class.getResource("/config/custom_messages/" + language + ".yml") == null
				|| I18nManager.class.getResource("/lang/" + language + ".yml") == null) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.language_not_supported", language));
			LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.contribute"));
			LOGGER.info(I18nManager.getDmccTranslation("utils.i18n.contribute_link"));
			return false;
		}

		return true;
	}

	private static boolean loadDmccTranslations() {
		String resourcePath = "/lang/" + language + ".yml";

		// Build into a fresh map and publish it with a single volatile write, so a concurrent reader can never
		// observe a partially filled translation table.
		Map<String, String> translations = new HashMap<>();

		try (InputStream inputStream = I18nManager.class.getResourceAsStream(resourcePath)) {
			JsonNode rootNode = YAML_MAPPER.readTree(inputStream);
			flattenJsonToMap(rootNode, "", translations);
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.i18n.load_failed", resourcePath), e);
			return false;
		}

		dmccTranslations = translations;
		return true;
	}

	private static boolean loadCustomMessages() {
		try {
			Files.createDirectories(CUSTOM_MESSAGES_DIR);
			Path customMessagesPath = CUSTOM_MESSAGES_DIR.resolve(language + ".yml");
			String templatePath = "/config/custom_messages/" + language + ".yml";

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

			JsonNode userMessages;
			try (Reader reader = Files.newBufferedReader(customMessagesPath, StandardCharsets.UTF_8)) {
				userMessages = YAML_MAPPER.readTree(reader);
			}

			JsonNode templateMessages;
			try (InputStream templateStream = I18nManager.class.getResourceAsStream(templatePath)) {
				templateMessages = YAML_MAPPER.readTree(templateStream);
			}

			// `errorOnUnmodified` is false because users might not need to customize messages.
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
	 * Gets a translation from DMCC's internal translation files (lang/*.yml). Placeholders use <code>{}</code>.
	 */
	public static String getDmccTranslation(String key, Object... args) {
		// Single volatile read of the published snapshot: the map is never mutated in place, so this is safe
		// without locking and can not observe a half-loaded table.
		String translation = dmccTranslations.getOrDefault(key, key);
		return StringUtils.format(translation, args);
	}

	public static JsonNode getCustomMessages() {
		return customMessages;
	}

	private static void flattenJsonToMap(JsonNode node, String path, Map<String, String> map) {
		if (node.isObject()) {
			String prefix = path.isEmpty() ? "" : path + ".";
			for (Map.Entry<String, JsonNode> field : node.properties()) {
				flattenJsonToMap(field.getValue(), prefix + field.getKey(), map);
			}
		} else if (node.isString()) {
			map.put(path, node.asString());
		}
	}
}
