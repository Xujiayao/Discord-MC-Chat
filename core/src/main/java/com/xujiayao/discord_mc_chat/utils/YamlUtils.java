package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
public final class YamlUtils {

	private static final List<String> REQUIRED_MODIFIED_KEYS = List.of(
			"discord.bot.token",
			"multi_server.server_name",
			"multi_server.connection.shared_secret"
	);

	/**
	 * Pre-compiled separator used to split configuration paths; {@code Pattern.split(key)} is the exact
	 * equivalent of the {@code key.split("\\.")} call it replaces.
	 */
	private static final Pattern PATH_SEPARATOR = Pattern.compile("\\.");

	private YamlUtils() {
	}

	/**
	 * Validates the loaded config against the template with optional check for modification.
	 *
	 * @param errorOnUnmodified If true, an error is logged if the file is identical to the template
	 */
	public static boolean validate(JsonNode userConfig, JsonNode templateConfig, boolean errorOnUnmodified) {
		if (errorOnUnmodified && userConfig.equals(templateConfig)) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.unmodified"));
			return false;
		}

		String configVersion = userConfig.path("version").asString();
		String templateVersion = templateConfig.path("version").asString();

		if (!templateVersion.equals(configVersion)) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.version_mismatch", templateVersion, configVersion));
			LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.upgrade_prompt"));
			return false;
		}

		// Sorted sets: the reported key lists must not depend on hash order.
		Set<String> missingKeys = new TreeSet<>();
		Set<String> extraKeys = new TreeSet<>();
		findKeyDiffs(templateConfig, userConfig, "", missingKeys, extraKeys);

		if (!extraKeys.isEmpty() || !missingKeys.isEmpty()) {
			if (!extraKeys.isEmpty()) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.unrecognized_keys"));
				for (String key : extraKeys) {
					LOGGER.error("  - {}", key);
				}
			}
			if (!missingKeys.isEmpty()) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.missing_keys"));
				for (String key : missingKeys) {
					LOGGER.error("  - {}", key);
				}
			}
			return false;
		}

		Set<String> typeIssues = validateNodeTypes(templateConfig, userConfig, "");
		if (!typeIssues.isEmpty()) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.type_mismatch"));
			for (String issue : typeIssues) {
				LOGGER.error("  - {}", issue);
			}
			return false;
		}

		// Hard-coded list of keys the user should modify, to catch keys left unchanged from the template
		Set<String> unmodifiedKeys = findUnmodifiedKeys(userConfig, templateConfig);
		if (!unmodifiedKeys.isEmpty()) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.yaml.unchanged_keys"));
			for (String key : unmodifiedKeys) {
				LOGGER.error("  - {}", key);
			}
			return false;
		}

		return true;
	}

	private static void findKeyDiffs(JsonNode template, JsonNode config, String path, Set<String> missingKeys, Set<String> extraKeys) {
		if (template.isObject() && config.isObject()) {
			// Check for missing keys (in template but not in config)
			forEachObjectField(template, path, (field, currentPath) -> {
				if (!config.has(field)) {
					missingKeys.add(currentPath);
				} else {
					findKeyDiffs(template.get(field), config.get(field), currentPath, missingKeys, extraKeys);
				}
			});
			// Check for extra keys (in config but not in template)
			forEachObjectField(config, path, (field, currentPath) -> {
				if (!template.has(field)) {
					extraKeys.add(currentPath);
				}
			});
		} else if (template.isArray() && config.isArray() && !template.isEmpty()) {
			// For arrays, check elements recursively by using the first template element as the reference
			JsonNode templateItem = template.get(0);
			for (int i = 0; i < config.size(); i++) {
				findKeyDiffs(templateItem, config.get(i), path + "[" + i + "]", missingKeys, extraKeys);
			}
		}
	}

	private static Set<String> validateNodeTypes(JsonNode template, JsonNode config, String path) {
		Set<String> issues = new HashSet<>();

		if (template.getNodeType() != config.getNodeType()) {
			// Special case: the user emptied an array; consider it valid
			if (!(template.isArray() && config.isNull())) {
				issues.add((path.isEmpty() ? "(root)" : path) + ": Expected type " + template.getNodeType()
						+ " but found " + config.getNodeType());
			}
			return issues; // If types mismatch, don't recurse further at this node
		}

		if (template.isObject()) {
			forEachObjectField(template, path, (fieldName, currentPath) -> {
				JsonNode templateValue = template.get(fieldName);
				JsonNode configValue = config.path(fieldName);

				if (!configValue.isMissingNode()) {
					issues.addAll(validateNodeTypes(templateValue, configValue, currentPath));
				}
			});
		}

		else if (template.isArray() && !template.isEmpty() && config.isArray()) {
			JsonNode templateItem = template.get(0);

			for (int i = 0; i < config.size(); i++) {
				JsonNode configItem = config.get(i);
				String currentPath = path + "[" + i + "]";

				issues.addAll(validateNodeTypes(templateItem, configItem, currentPath));
			}
		}

		return issues;
	}

	private static Set<String> findUnmodifiedKeys(JsonNode config, JsonNode templateConfig) {
		Set<String> unmodifiedKeys = new HashSet<>();

		for (String key : YamlUtils.REQUIRED_MODIFIED_KEYS) {
			String[] parts = PATH_SEPARATOR.split(key);
			JsonNode configNode = config;
			JsonNode templateNode = templateConfig;

			for (String part : parts) {
				configNode = configNode.path(part);
				templateNode = templateNode.path(part);
			}

			// A missing node just means different modes, or language vs config files, have different keys.
			if (configNode.isMissingNode() || templateNode.isMissingNode()) {
				continue;
			}

			if (configNode.equals(templateNode)) {
				unmodifiedKeys.add(key);
			}
		}

		return unmodifiedKeys;
	}

	private static void forEachObjectField(JsonNode node, String path, BiConsumer<String, String> action) {
		for (Map.Entry<String, JsonNode> field : node.properties()) {
			String fieldName = field.getKey();
			String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;
			action.accept(fieldName, currentPath);
		}
	}
}
