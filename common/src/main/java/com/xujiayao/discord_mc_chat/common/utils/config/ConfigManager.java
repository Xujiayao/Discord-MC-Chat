package com.xujiayao.discord_mc_chat.common.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.xujiayao.discord_mc_chat.common.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

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

	private static Path configPath;
	private static JsonNode config;

	/**
	 * Initializes the configuration system.
	 * Copies the default config if none exists, loads and validates the config.
	 *
	 * @return true if initialization was successful, false otherwise
	 */
	public static boolean initialize() {
		try {
			// Create directories if they do not exist
			Path configDir = Paths.get(CONFIG_DIR);
			Files.createDirectories(configDir);

			// Setup YAML mapper
			YAMLFactory yamlFactory = new YAMLFactory()
					.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
					.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
			ObjectMapper mapper = new ObjectMapper(yamlFactory);

			configPath = configDir.resolve(CONFIG_FILE);
			boolean configExists = Files.exists(configPath);

			// If config does not exist or is empty, copy the template
			if (!configExists || Files.size(configPath) == 0) {
				LOGGER.warn("Configuration file does not exist or is empty");

				copyDefaultConfig(configPath);

				LOGGER.info("Created default configuration file at \"{}\"", configPath);
				LOGGER.info("Please edit the configuration file before restarting {}", (Utils.isMinecraftEnvironment() ? "the Minecraft server" : "DMCC"));
				return false;
			}

			// Load the user's config
			config = mapper.readTree(Files.newBufferedReader(configPath, StandardCharsets.UTF_8));

			// Load the template config for validation
			JsonNode templateConfig;
			try (InputStream templateStream = ConfigManager.class.getResourceAsStream(CONFIG_TEMPLATE)) {
				if (templateStream == null) {
					LOGGER.error("Could not find configuration template in resources: {}", CONFIG_TEMPLATE);
					return false;
				}
				templateConfig = mapper.readTree(templateStream);
			}

			// Validate config
			return validateConfig(templateConfig);
		} catch (IOException e) {
			LOGGER.error("Failed to initialize configuration", e);
			return false;
		}
	}

	/**
	 * Validates the loaded config against the template.
	 * Checks if config is identical to template or if versions do not match.
	 * Also verifies that the structure of the config matches the template.
	 *
	 * @param templateConfig The template config to validate against
	 * @return true if the config is valid, false otherwise
	 */
	private static boolean validateConfig(JsonNode templateConfig) {
		// Check if config is identical to template (user made no changes)
		if (config.equals(templateConfig)) {
			LOGGER.error("Configuration file has not been modified from default template");
			LOGGER.info("Please edit the file at \"{}\"", configPath);
			return false;
		}

		// Check config version
		String configVersion = config.path("version").asText(null);
		String templateVersion = templateConfig.path("version").asText(null);

		if (configVersion == null && templateVersion == null) {
			LOGGER.error("Failed to find valid \"version\" in both user config and template config");
			LOGGER.error("This is a bug in DMCC. Please report this issue!");
			return false;
		} else if (configVersion == null) {
			LOGGER.error("User configuration file is missing the required \"version\" field");
			return false;
		} else if (templateVersion == null) {
			LOGGER.error("Template configuration file is missing the required \"version\" field");
			LOGGER.error("This is a bug in DMCC. Please report this issue!");
			return false;
		} else if (!templateVersion.equals(configVersion)) {
			LOGGER.error("Configuration version mismatch. Expected version: {}, Found version: {}", templateVersion, configVersion);
			LOGGER.info("Please upgrade your configuration file");
			return false;
		}

		// Check for missing and extra keys in the user's config
		Set<String> missingKeys = new HashSet<>();
		Set<String> extraKeys = new HashSet<>();
		findKeyDiffs(templateConfig, config, "", missingKeys, extraKeys);

		if (!extraKeys.isEmpty()) {
			LOGGER.warn("Your configuration file contains the following unrecognized keys:");
			for (String key : extraKeys) {
				LOGGER.warn("  - {}", key);
			}
			LOGGER.warn("These keys will be ignored. However, you are recommended to remove them to avoid confusion!");
		}

		if (!missingKeys.isEmpty()) {
			LOGGER.error("Your configuration file is missing the following required keys:");
			for (String key : missingKeys) {
				LOGGER.error("  - {}", key);
			}
			return false;
		}

		// Check all node types for all items recursively
		Set<String> typeIssues = validateNodeTypes(templateConfig, config, "");
		if (!typeIssues.isEmpty()) {
			LOGGER.error("Your configuration file has type mismatch issues:");
			for (String issue : typeIssues) {
				LOGGER.error("  - {}", issue);
			}
			return false;
		}

		return true;
	}

	/**
	 * Recursively finds missing and extra keys between the template and the user config.
	 *
	 * @param template    The template node
	 * @param config      The user config node
	 * @param path        The current path in the configuration hierarchy
	 * @param missingKeys Set to accumulate missing keys (present in template but not in config)
	 * @param extraKeys   Set to accumulate extra keys (present in config but not in template)
	 */
	private static void findKeyDiffs(JsonNode template, JsonNode config, String path, Set<String> missingKeys, Set<String> extraKeys) {
		if (template.isObject() && config.isObject()) {
			// Check for missing keys (in template but not in config)
			Iterator<String> templateFields = template.fieldNames();
			while (templateFields.hasNext()) {
				String field = templateFields.next();
				String currentPath = path.isEmpty() ? field : path + "." + field;
				if (!config.has(field)) {
					missingKeys.add(currentPath);
				} else {
					findKeyDiffs(template.get(field), config.get(field), currentPath, missingKeys, extraKeys);
				}
			}
			// Check for extra keys (in config but not in template)
			Iterator<String> configFields = config.fieldNames();
			while (configFields.hasNext()) {
				String field = configFields.next();
				String currentPath = path.isEmpty() ? field : path + "." + field;
				if (!template.has(field)) {
					extraKeys.add(currentPath);
				}
			}
		} else if (template.isArray() && config.isArray() && !template.isEmpty()) {
			// For arrays, check elements recursively by using the first template element as the reference
			JsonNode templateItem = template.get(0);
			for (int i = 0; i < config.size(); i++) {
				findKeyDiffs(templateItem, config.get(i), path + "[" + i + "]", missingKeys, extraKeys);
			}
		}
	}

	/**
	 * Recursively validates all node types in the config against the template.
	 *
	 * @param template The template node
	 * @param config   The user config node
	 * @param path     The current path in the configuration hierarchy
	 * @return A set of type mismatch or structural issues
	 */
	private static Set<String> validateNodeTypes(JsonNode template, JsonNode config, String path) {
		Set<String> issues = new HashSet<>();

		// Check for direct node type mismatch (covers string/object/array/etc.)
		if (template.getNodeType() != config.getNodeType()) {
			issues.add((path.isEmpty() ? "(root)" : path) + ": Expected type " + template.getNodeType()
					+ " but found " + config.getNodeType());
			return issues; // If types mismatch, don't recurse further at this node
		}

		// If the node is an object, recurse for each field
		if (template.isObject()) {
			Iterator<String> fieldNames = template.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

				JsonNode templateValue = template.get(fieldName);
				JsonNode configValue = config.path(fieldName);

				if (!configValue.isMissingNode()) {
					issues.addAll(validateNodeTypes(templateValue, configValue, currentPath));
				}
			}
		}

		// If the node is an array, check each element against the template's first element (if present)
		else if (template.isArray() && !template.isEmpty() && config.isArray()) {
			JsonNode templateItem = template.get(0);

			for (int i = 0; i < config.size(); i++) {
				JsonNode configItem = config.get(i);
				String currentPath = path + "[" + i + "]";

				// Recursively validate each array element
				issues.addAll(validateNodeTypes(templateItem, configItem, currentPath));
			}
		}

		return issues;
	}

	/**
	 * Copies the default configuration file to the specified path.
	 *
	 * @param configPath The path to copy the default config to
	 * @throws IOException If an I/O error occurs
	 */
	private static void copyDefaultConfig(Path configPath) throws IOException {
		try (InputStream inputStream = ConfigManager.class.getResourceAsStream(CONFIG_TEMPLATE)) {
			if (inputStream == null) {
				throw new IOException("Default config template not found: " + CONFIG_TEMPLATE);
			}
			Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);
		}
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
