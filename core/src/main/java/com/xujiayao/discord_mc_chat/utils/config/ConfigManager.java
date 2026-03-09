package com.xujiayao.discord_mc_chat.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Configuration manager for DMCC.
 * Handles loading, validation, and access to configuration values.
 *
 * @author Xujiayao
 */
public class ConfigManager {

	private static final Path CONFIG_FILE_PATH = Paths.get("./config/discord_mc_chat/config.yml");
	private static JsonNode config;

	/**
	 * Loads the configuration file based on the determined operating mode.
	 *
	 * @return true if the config was loaded and validated successfully, false otherwise.
	 */
	public static boolean load() {
		String expectedMode = ModeManager.getMode();
		String configTemplatePath = "/config/config_" + expectedMode + ".yml";

		try {
			// Create directories if they do not exist
			Files.createDirectories(CONFIG_FILE_PATH.getParent());

			// If config.yml does not exist or is empty, create it from the appropriate template.
			if (!Files.exists(CONFIG_FILE_PATH) || Files.size(CONFIG_FILE_PATH) == 0) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.not_found"));
				LOGGER.info(I18nManager.getDmccTranslation("utils.config.config.creating", CONFIG_FILE_PATH));
				LOGGER.info(I18nManager.getDmccTranslation("utils.config.config.edit_prompt", CONFIG_FILE_PATH));

				try (InputStream inputStream = ConfigManager.class.getResourceAsStream(configTemplatePath)) {
					if (inputStream == null) {
						throw new IOException("Default config template not found: " + configTemplatePath);
					}

					// Read the template content
					String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

					// Replace the default language with the detected system language.
					// This ensures the generated config file uses the user's system language if supported.
					template = template.replace("language: \"to_be_auto_replaced\"", StringUtils.format("language: \"{}\"", I18nManager.getLanguage()));

					// If in standalone mode, generate a secure random shared secret
					if ("standalone".equals(expectedMode)) {
						String randomSecret = CryptUtils.generateRandomString(32);
						template = template.replace("shared_secret: \"to_be_auto_replaced\"", StringUtils.format("shared_secret: \"{}\"", randomSecret));
					}

					// Write the config file with the replaced language setting
					Files.writeString(CONFIG_FILE_PATH, template, StandardCharsets.UTF_8);
				}

				return false;
			}

			// Load the user's config.yml
			JsonNode userConfig = YAML_MAPPER.readTree(Files.newBufferedReader(CONFIG_FILE_PATH, StandardCharsets.UTF_8));

			// Check for mode consistency
			String configMode = userConfig.path("mode").asText();
			if (!expectedMode.equals(configMode)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.mode_mismatch"));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.mode_mismatch_detail", expectedMode, configMode));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.backup_prompt"));
				return false;
			}

			// Load the corresponding template for validation
			JsonNode templateConfig;
			try (InputStream templateStream = ConfigManager.class.getResourceAsStream(configTemplatePath)) {
				if (templateStream == null) {
					throw new IOException("Default config template not found: " + configTemplatePath);
				}
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate config
			if (!YamlUtils.validate(userConfig, templateConfig, true)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.validation_failed"));
				return false;
			}

			ConfigManager.config = userConfig;
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.load_failed"), e);
			return false;
		}
	}

	/**
	 * Gets a specific configuration value as a JsonNode.
	 *
	 * @param path The path to the configuration value
	 * @return The JsonNode at the specified path
	 */
	public static JsonNode getConfigNode(String path) {
		if (config == null) {
			// This can happen if config is not loaded yet.
			// Returning a missing node is safer than a NullPointerException.
			return YAML_MAPPER.missingNode();
		}

		String[] parts = path.split("\\.");
		JsonNode node = config;

		for (String part : parts) {
			if (node == null || node.isMissingNode() || node.isNull()) {
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.path_not_found", path));
				return node;
			}
			node = node.path(part);
		}

		return node;
	}

	/**
	 * Generic method to get a configuration value with specified conversion function.
	 *
	 * @param <T>       The type to convert the configuration value to
	 * @param path      The path to the configuration value
	 * @param converter Function to convert JsonNode to the desired type
	 * @return The value at the specified path converted to type T, or null if not found
	 */
	public static <T> T getValue(String path, Function<JsonNode, T> converter) {
		JsonNode node = getConfigNode(path);

		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		return converter.apply(node);
	}

	/**
	 * Gets a configuration value as a string.
	 *
	 * @param path The path to the configuration value
	 * @return The string value at the specified path, or null if not found
	 */
	public static String getString(String path) {
		return getValue(path, JsonNode::asText);
	}

	/**
	 * Gets a configuration value as a string, with a default value if not found.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path is not found
	 * @return The string value at the specified path, or null if not found
	 */
	public static String getString(String path, String defaultValue) {
		String value = getValue(path, JsonNode::asText);
		return value == null ? defaultValue : value;
	}

	/**
	 * Gets a configuration value as an integer.
	 *
	 * @param path The path to the configuration value
	 * @return The integer value at the specified path
	 */
	public static Integer getInt(String path) {
		return getValue(path, JsonNode::asInt);
	}

	/**
	 * Gets a configuration value as an integer, with a default value if not found.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path is not found
	 * @return The integer value at the specified path
	 */
	public static Integer getInt(String path, int defaultValue) {
		Integer value = getValue(path, JsonNode::asInt);
		return value == null ? defaultValue : value;
	}

	/**
	 * Gets a configuration value as a boolean.
	 *
	 * @param path The path to the configuration value
	 * @return The boolean value at the specified path
	 */
	public static Boolean getBoolean(String path) {
		return getValue(path, JsonNode::asBoolean);
	}
}
