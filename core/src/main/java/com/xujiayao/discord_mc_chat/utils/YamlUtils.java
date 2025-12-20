package com.xujiayao.discord_mc_chat.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * YAML utility class.
 *
 * @author Xujiayao
 */
public class YamlUtils {

	private static final List<String> REQUIRED_MODIFIED_KEYS = List.of(
			"discord.bot.token",
			"multi_server.server_name"
	);

	/**
	 * Validates the loaded config against the template with optional check for modification.
	 *
	 * @param userConfig        The user-loaded config to validate
	 * @param templateConfig    The template config to validate against
	 * @param configPath        The path to the config file for logging purposes
	 * @param errorOnUnmodified If true, an error is logged if the file is identical to the template
	 * @return true if the config is valid, false otherwise
	 */
	public static boolean validate(JsonNode userConfig, JsonNode templateConfig, Path configPath, boolean errorOnUnmodified) {
		LOGGER.info("Validating configuration file at \"{}\"...", configPath);

		// Check if config is identical to template (user made no changes)
		if (errorOnUnmodified && userConfig.equals(templateConfig)) {
			LOGGER.error("Configuration file has not been modified from default template");
			return false;
		}

		// Check config version
		String configVersion = userConfig.path("version").asText();
		String templateVersion = templateConfig.path("version").asText();

		if (!templateVersion.equals(configVersion)) {
			LOGGER.error("Configuration version mismatch. Expected version: {}, Found version: {}", templateVersion, configVersion);
			LOGGER.error("Please upgrade your configuration file on Discord-MC-Chat Docs");
			return false;
		}

		// Check for missing and extra keys in the user's config
		Set<String> missingKeys = new HashSet<>();
		Set<String> extraKeys = new HashSet<>();
		findKeyDiffs(templateConfig, userConfig, "", missingKeys, extraKeys);

		if (!extraKeys.isEmpty() || !missingKeys.isEmpty()) {
			if (!extraKeys.isEmpty()) {
				LOGGER.warn("Your configuration file contains the following unrecognized keys:");
				for (String key : extraKeys) {
					LOGGER.warn("  - {}", key);
				}
			}
			if (!missingKeys.isEmpty()) {
				LOGGER.error("Your configuration file is missing the following required keys:");
				for (String key : missingKeys) {
					LOGGER.error("  - {}", key);
				}
			}
			return false;
		}

		// Check all node types for all items recursively
		Set<String> typeIssues = validateNodeTypes(templateConfig, userConfig, "");
		if (!typeIssues.isEmpty()) {
			LOGGER.error("Your configuration file has type mismatch issues:");
			for (String issue : typeIssues) {
				LOGGER.error("  - {}", issue);
			}
			return false;
		}

		// A hard-coded list of keys that should be modified by the user
		// This is to catch cases where the user leaves a key unchanged from the template
		Set<String> unmodifiedKeys = findUnmodifiedKeys(userConfig, templateConfig);
		if (!unmodifiedKeys.isEmpty()) {
			LOGGER.error("The following configuration keys are still unchanged from the template:");
			for (String key : unmodifiedKeys) {
				LOGGER.error("  - {}", key);
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
	 * Finds keys from a given list that have not been changed from the template config.
	 *
	 * @param config         The user config node
	 * @param templateConfig The template config node
	 * @return A set of keys that are unmodified
	 */
	private static Set<String> findUnmodifiedKeys(JsonNode config, JsonNode templateConfig) {
		Set<String> unmodifiedKeys = new HashSet<>();

		for (String key : YamlUtils.REQUIRED_MODIFIED_KEYS) {
			String[] parts = key.split("\\.");
			JsonNode configNode = config;
			JsonNode templateNode = templateConfig;

			for (String part : parts) {
				configNode = configNode.path(part);
				templateNode = templateNode.path(part);
			}

			// If either node is missing, it is the case that config files of different modes have different keys.
			// Or it is the case that language files and config files have different keys.
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
