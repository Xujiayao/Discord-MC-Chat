package com.xujiayao.discord_mc_chat.common.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static com.xujiayao.discord_mc_chat.common.Constants.LOGGER;

/**
 * YAML utility class.
 *
 * @author Xujiayao
 */
public class YamlUtils {

	/**
	 * Validates the loaded config against the template.
	 * Checks if config is identical to template or if versions do not match.
	 * Also verifies that the structure of the config matches the template.
	 *
	 * @param config         The user-loaded config to validate
	 * @param templateConfig The template config to validate against
	 * @param configPath     The path to the config file for logging purposes
	 * @return true if the config is valid, false otherwise
	 */
	public static boolean validate(JsonNode config, JsonNode templateConfig, Path configPath) {
		return validate(config, templateConfig, configPath, true);
	}

	/**
	 * Validates the loaded config against the template with optional check for modification.
	 *
	 * @param config            The user-loaded config to validate
	 * @param templateConfig    The template config to validate against
	 * @param configPath        The path to the config file for logging purposes
	 * @param errorOnUnmodified If true, an error is logged if the file is identical to the template
	 * @return true if the config is valid, false otherwise
	 */
	public static boolean validate(JsonNode config, JsonNode templateConfig, Path configPath, boolean errorOnUnmodified) {
		LOGGER.info("Validating configuration file at \"{}\"...", configPath);

		// Check if config is identical to template (user made no changes)
		if (errorOnUnmodified && config.equals(templateConfig)) {
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

		// A hard-coded list of keys that should be modified by the user
		// This is to catch cases where the user leaves a key unchanged from the template
		String[] keysToCheck = {
				"bot.token"
		};
		Set<String> unmodifiedKeys = findUnmodifiedKeys(config, templateConfig, keysToCheck);
		if (!unmodifiedKeys.isEmpty()) {
			LOGGER.error("The following configuration keys are still unchanged from the template:");
			for (String key : unmodifiedKeys) {
				LOGGER.error("  - {}", key);
			}
			LOGGER.info("Please modify them in the configuration file at \"{}\"", configPath);
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
	 * Finds keys from a given list that have not been changed from the template config.
	 *
	 * @param config         The user config node
	 * @param templateConfig The template config node
	 * @param keysToCheck    An array of dot-separated keys to check for modifications
	 * @return A set of keys that are unmodified
	 */
	private static Set<String> findUnmodifiedKeys(JsonNode config, JsonNode templateConfig, String[] keysToCheck) {
		Set<String> unmodifiedKeys = new HashSet<>();

		for (String key : keysToCheck) {
			String[] parts = key.split("\\.");
			JsonNode configNode = config;
			JsonNode templateNode = templateConfig;

			for (String part : parts) {
				configNode = configNode.path(part);
				templateNode = templateNode.path(part);
			}

			// If either node is missing, it's not an "unmodified" key in the sense we're checking.
			// The missing key itself is handled by findKeyDiffs.
			if (configNode.isMissingNode() || templateNode.isMissingNode()) {
				continue;
			}

			if (configNode.equals(templateNode)) {
				unmodifiedKeys.add(key);
			}
		}

		return unmodifiedKeys;
	}
}
