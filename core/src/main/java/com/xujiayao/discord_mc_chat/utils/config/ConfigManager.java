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

	private static final Path CONFIG_PATH = Paths.get("./config/discord_mc_chat/config.yml");
	private static JsonNode config;

	/**
	 * Loads the configuration file based on the determined operating mode.
	 *
	 * @param expectedMode The mode DMCC is expected to run in.
	 * @return true if the config was loaded and validated successfully, false otherwise.
	 */
	public static boolean load(String expectedMode) {
		try {
			// Create directories if they do not exist
			Files.createDirectories(CONFIG_PATH.getParent());

			// If config.yml does not exist or is empty, create it from the appropriate template.
			if (!Files.exists(CONFIG_PATH) || Files.size(CONFIG_PATH) == 0) {
				createDefaultConfig(expectedMode);
				return false;
			}

			// Load the user's config.yml
			JsonNode userConfig = YAML_MAPPER.readTree(Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8));

			// Check for mode consistency
			String configMode = userConfig.path("mode").asText();
			if (!expectedMode.equals(configMode)) {
				LOGGER.error("Mode mismatch detected!");
				LOGGER.error("The expected mode is \"{}\" (from mode.yml or environment), but config.yml is for \"{}\".", expectedMode, configMode);
				LOGGER.error("Please backup and delete your existing config.yml to allow DMCC to generate a new and correct one, then run \"/dmcc reload\".");
				return false;
			}

			// Load the corresponding template for validation
			String templatePath = "/config/config_" + expectedMode + ".yml";
			JsonNode templateConfig;
			try (InputStream templateStream = ConfigManager.class.getResourceAsStream(templatePath)) {
				if (templateStream == null) {
					LOGGER.error("Could not find configuration template in resources: {}", templatePath);
					return false;
				}
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate config
			if (YamlUtils.validate(userConfig, templateConfig, CONFIG_PATH)) {
				ConfigManager.config = userConfig;
				LOGGER.info("Configuration loaded successfully!");

				return true;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load or validate configuration", e);
		}

		return false;
	}

	/**
	 * Creates a default config.yml from a template based on the mode.
	 * For standalone mode, it also generates and injects a random shared_secret.
	 *
	 * @param mode The operating mode which determines the template to use.
	 */
	private static void createDefaultConfig(String mode) throws IOException {
		String templateName = "/config/config_" + mode + ".yml";
		LOGGER.warn("Configuration file not found or is empty. Creating a new one for \"{}\" mode.", mode);

		try (InputStream inputStream = ConfigManager.class.getResourceAsStream(templateName)) {
			if (inputStream == null) {
				throw new IOException("Default config template not found: " + templateName);
			}

			// Copy the template config file as is
			Files.copy(inputStream, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
		}

		LOGGER.info("Created default configuration file at \"{}\"", CONFIG_PATH);
		LOGGER.info("Please edit the configuration file before reloading or restarting DMCC");
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
			if (node == null || node.isMissingNode()) {
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
