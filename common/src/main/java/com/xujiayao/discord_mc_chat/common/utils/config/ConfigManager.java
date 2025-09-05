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
		int configVersion = config.path("config_version").asInt(-1);
		int templateVersion = templateConfig.path("config_version").asInt(-1);

		if (configVersion == -1 && templateVersion == -1) {
			LOGGER.error("Failed to find valid \"config_version\" in both user config and template config");
			LOGGER.error("This is a bug in DMCC. Please report this issue!");
			return false;
		} else if (configVersion == -1) {
			LOGGER.error("User configuration file is missing the required \"config_version\" field");
			return false;
		} else if (templateVersion == -1) {
			LOGGER.error("Template configuration file is missing the required \"config_version\" field");
			LOGGER.error("This is a bug in DMCC. Please report this issue!");
			return false;
		} else if (configVersion != templateVersion) {
			LOGGER.error("Configuration version mismatch. Expected version: {}, Found version: {}", templateVersion, configVersion);
			LOGGER.info("Please upgrade your configuration file");
			return false;
		}

		// Check for extra keys in the user's config
		Set<String> extraKeys = findExtraKeys(templateConfig, config, "");
		if (!extraKeys.isEmpty()) {
			LOGGER.warn("Your configuration file contains the following unrecognized keys:");
			for (String key : extraKeys) {
				LOGGER.warn("  - {}", key);
			}
			LOGGER.warn("These keys will be ignored. However, you are recommended to remove them to avoid confusion!");
		}

		// Check if the structure of the config matches the template
		Set<String> missingKeys = findMissingKeys(templateConfig, config, "");
		if (!missingKeys.isEmpty()) {
			LOGGER.error("Your configuration file is missing the following required keys:");
			for (String key : missingKeys) {
				LOGGER.error("  - {}", key);
			}
			return false;
		}

		// Check arrays for required field structure, including type validation
		Set<String> arrayStructureIssues = validateArrayStructures(templateConfig, config, "");
		if (!arrayStructureIssues.isEmpty()) {
			LOGGER.error("Your configuration file has issues in array structures:");
			for (String issue : arrayStructureIssues) {
				LOGGER.error("  - {}", issue);
			}
			return false;
		}

		return true;
	}

	/**
	 * Recursively validates that all required keys in the template are present in the user config.
	 *
	 * @param template The template node
	 * @param config   The user config node
	 * @param path     The current path in the configuration hierarchy
	 * @return A set of missing keys in dot notation
	 */
	private static Set<String> findMissingKeys(JsonNode template, JsonNode config, String path) {
		Set<String> missingKeys = new HashSet<>();

		if (template.isObject()) {
			Iterator<String> fieldNames = template.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

				JsonNode templateValue = template.get(fieldName);
				JsonNode configValue = config.path(fieldName);

				if (configValue.isMissingNode()) {
					// This key is missing in the user's config
					missingKeys.add(currentPath);
				} else if (templateValue.isObject() && !templateValue.isEmpty()) {
					// Recursively check nested objects
					missingKeys.addAll(findMissingKeys(templateValue, configValue, currentPath));
				} else if (templateValue.isArray() && !templateValue.isEmpty() &&
						templateValue.get(0).isObject() && !configValue.isArray()) {
					// Check if an array of objects in template is missing in config
					missingKeys.add(currentPath);
				}
			}
		}

		return missingKeys;
	}

	/**
	 * Recursively finds extra keys in the user config that are not present in the template.
	 *
	 * @param template The template node
	 * @param config   The user config node
	 * @param path     The current path in the configuration hierarchy
	 * @return A set of extra keys in dot notation
	 */
	private static Set<String> findExtraKeys(JsonNode template, JsonNode config, String path) {
		Set<String> extraKeys = new HashSet<>();

		if (config.isObject()) {
			Iterator<String> fieldNames = config.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

				JsonNode templateValue = template.path(fieldName);
				JsonNode configValue = config.get(fieldName);

				if (templateValue.isMissingNode()) {
					// This key exists in config but not in template
					extraKeys.add(currentPath);
				} else if (configValue.isObject() && templateValue.isObject()) {
					// Recursively check nested objects
					extraKeys.addAll(findExtraKeys(templateValue, configValue, currentPath));
				}
			}
		}

		return extraKeys;
	}

	/**
	 * Validates the structure of arrays in the configuration, ensuring each array item
	 * has all required fields as specified in the template, and each field matches the expected type.
	 *
	 * @param template The template node
	 * @param config   The user config node
	 * @param path     The current path in the configuration hierarchy
	 * @return A set of issues found in array structures
	 */
	private static Set<String> validateArrayStructures(JsonNode template, JsonNode config, String path) {
		Set<String> issues = new HashSet<>();

		// Paths to arrays of objects that should be validated
		Set<String> arrayPathsToValidate = new HashSet<>();
		arrayPathsToValidate.add("channels");
		arrayPathsToValidate.add("custom_messages.templates");

		if (template.isObject()) {
			Iterator<String> fieldNames = template.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

				JsonNode templateValue = template.get(fieldName);
				JsonNode configValue = config.path(fieldName);

				if (configValue.isMissingNode()) {
					continue; // Skip missing nodes, already reported elsewhere
				}

				if (templateValue.isObject()) {
					// Recursively check nested objects
					issues.addAll(validateArrayStructures(templateValue, configValue, currentPath));
				} else if (templateValue.isArray() && configValue.isArray()
						&& !templateValue.isEmpty() && templateValue.get(0).isObject()
						&& arrayPathsToValidate.contains(currentPath)) {

					// Validate arrays of objects
					JsonNode templateItem = templateValue.get(0); // Use first item as template

					// Get required fields from template item
					Set<String> requiredFields = new HashSet<>();
					templateItem.fieldNames().forEachRemaining(requiredFields::add);

					// Check each item in user's config array
					for (int i = 0; i < configValue.size(); i++) {
						JsonNode configItem = configValue.get(i);
						if (!configItem.isObject()) {
							issues.add(currentPath + "[" + i + "]: Expected an object but found " +
									configItem.getNodeType());
							continue;
						}

						// Check for required fields
						for (String requiredField : requiredFields) {
							JsonNode templateField = templateItem.get(requiredField);
							JsonNode configField = configItem.path(requiredField);

							if (configField.isMissingNode()) {
								issues.add(currentPath + "[" + i + "]: Missing required field '" + requiredField + "'");
							} else if (templateField != null &&
									templateField.getNodeType() != configField.getNodeType()) {
								// Dynamically check type
								issues.add(currentPath + "[" + i + "]." + requiredField +
										": Expected type " + templateField.getNodeType() +
										" but found " + configField.getNodeType());
							}
						}

						// Check for extra fields
						Set<String> extraFields = new HashSet<>();
						configItem.fieldNames().forEachRemaining(field -> {
							if (!requiredFields.contains(field)) {
								extraFields.add(field);
							}
						});

						if (!extraFields.isEmpty()) {
							issues.add(currentPath + "[" + i + "]: Contains unrecognized fields: " +
									String.join(", ", extraFields));
						}
					}
				}
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
