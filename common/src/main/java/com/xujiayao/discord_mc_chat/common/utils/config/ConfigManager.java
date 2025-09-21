package com.xujiayao.discord_mc_chat.common.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.common.utils.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.common.DMCC.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;
import static com.xujiayao.discord_mc_chat.common.DMCC.YAML_MAPPER;

/**
 * Configuration manager for DMCC.
 * Handles loading, validation, and access to configuration values.
 *
 * @author Xujiayao
 */
public class ConfigManager {

	private static final String CONFIG_DIR = "./config/discord_mc_chat";
	private static final String CONFIG_FILE = "config.yml";
	private static final String CONFIG_TEMPLATE = "/config/config.yml";

	private static JsonNode config;

	/**
	 * Loads the configuration file.
	 * If the config file does not exist or is empty, it copies the default template.
	 *
	 * @return true if the config was loaded and validated successfully, false otherwise
	 */
	public static boolean load() {
		try {
			// Create directories if they do not exist
			Path configDir = Paths.get(CONFIG_DIR);
			Files.createDirectories(configDir);

			Path configPath = configDir.resolve(CONFIG_FILE);
			boolean configExists = Files.exists(configPath);

			// If config does not exist or is empty, copy the template
			if (!configExists || Files.size(configPath) == 0) {
				LOGGER.warn("Configuration file does not exist or is empty");

				try (InputStream inputStream = ConfigManager.class.getResourceAsStream(CONFIG_TEMPLATE)) {
					if (inputStream == null) {
						throw new IOException("Default config template not found: " + CONFIG_TEMPLATE);
					}
					Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);
				}

				LOGGER.info("Created default configuration file at \"{}\"", configPath);
				LOGGER.info("Please edit the configuration file before restarting {}", (IS_MINECRAFT_ENV ? "the Minecraft server" : "DMCC"));
				// TODO 不需要重启，写完配置文件后直接热加载
				return false;
			}

			// Load the user's config
			JsonNode config = YAML_MAPPER.readTree(Files.newBufferedReader(configPath, StandardCharsets.UTF_8));

			// Load the template config for validation
			JsonNode templateConfig;
			try (InputStream templateStream = ConfigManager.class.getResourceAsStream(CONFIG_TEMPLATE)) {
				if (templateStream == null) {
					LOGGER.error("Could not find configuration template in resources: {}", CONFIG_TEMPLATE);
					return false;
				}
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate config
			if (YamlUtils.validate(config, templateConfig, configPath)) {
				ConfigManager.config = config;
				LOGGER.info("Configuration loaded successfully!");

				return true;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load configuration", e);
		}

		return false;
	}

	/**
	 * Gets the root JsonNode of the configuration.
	 *
	 * @return The root JsonNode
	 */
	public static JsonNode getConfig() {
		return config;
	}

	/**
	 * Gets a specific configuration value as a JsonNode.
	 *
	 * @param path The path to the configuration value
	 * @return The JsonNode at the specified path
	 */
	public static JsonNode getConfigNode(String path) {
		String[] parts = path.split("\\.");
		JsonNode node = config;

		for (String part : parts) {
			node = node.path(part);
			if (node.isMissingNode()) {
				LOGGER.warn("Configuration path not found: {}", path);
				return node;
			}
		}

		return node;
	}

	/**
	 * Generic method to get a configuration value with specified conversion function.
	 *
	 * @param <T>          The type to convert the configuration value to
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path does not exist
	 * @param converter    Function to convert JsonNode to the desired type
	 * @return The value at the specified path converted to type T, or defaultValue if not found
	 */
	public static <T> T getValue(String path, T defaultValue, Function<JsonNode, T> converter) {
		JsonNode node = getConfigNode(path);
		return node.isMissingNode() ? defaultValue : converter.apply(node);
	}

	/**
	 * Gets a configuration value as a string.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path does not exist
	 * @return The string value at the specified path, or defaultValue if not found
	 */
	public static String getString(String path, String defaultValue) {
		return getValue(path, defaultValue, JsonNode::asText);
	}

	/**
	 * Gets a configuration value as an integer.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path does not exist
	 * @return The integer value at the specified path, or defaultValue if not found
	 */
	public static int getInt(String path, int defaultValue) {
		return getValue(path, defaultValue, JsonNode::asInt);
	}

	/**
	 * Gets a configuration value as a boolean.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path does not exist
	 * @return The boolean value at the specified path, or defaultValue if not found
	 */
	public static boolean getBoolean(String path, boolean defaultValue) {
		return getValue(path, defaultValue, JsonNode::asBoolean);
	}
}
