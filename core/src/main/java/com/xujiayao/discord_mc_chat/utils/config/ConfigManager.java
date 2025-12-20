package com.xujiayao.discord_mc_chat.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
				LOGGER.warn("Configuration file not found or is empty");
				LOGGER.warn("Creating a default one at \"{}\"", CONFIG_FILE_PATH);
				LOGGER.warn("Please edit \"{}\" before reloading DMCC", CONFIG_FILE_PATH);

				try (InputStream inputStream = ConfigManager.class.getResourceAsStream(configTemplatePath)) {
					if (inputStream == null) {
						throw new IOException("Default config template not found: " + configTemplatePath);
					}

					// Copy the template config file as is
					Files.copy(inputStream, CONFIG_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
				}

				return false;
			}

			// Load the user's config.yml
			JsonNode userConfig = YAML_MAPPER.readTree(Files.newBufferedReader(CONFIG_FILE_PATH, StandardCharsets.UTF_8));

			// Check for mode consistency
			String configMode = userConfig.path("mode").asText();
			if (!expectedMode.equals(configMode)) {
				LOGGER.error("Mode mismatch detected!");
				LOGGER.error("The expected mode is \"{}\" (from mode.yml or environment), but config.yml is for \"{}\".", expectedMode, configMode);
				LOGGER.error("Please backup and delete your existing config.yml to allow DMCC to generate a new and correct one.");
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
			if (!YamlUtils.validate(userConfig, templateConfig, CONFIG_FILE_PATH, true)) {
				LOGGER.error("Validation of config.yml failed");
				return false;
			}

			ConfigManager.config = userConfig;
			LOGGER.info("Configuration loaded successfully!");

			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to load or validate configuration", e);
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
				LOGGER.warn("Configuration path not found: {}", path);
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
	 * Gets a configuration value as an integer.
	 *
	 * @param path The path to the configuration value
	 * @return The integer value at the specified path
	 */
	public static Integer getInt(String path) {
		return getValue(path, JsonNode::asInt);
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
