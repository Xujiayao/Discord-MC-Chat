package com.xujiayao.discord_mc_chat.config;

import com.xujiayao.discord_mc_chat.utils.CryptUtils;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

public final class ConfigManager {

	private static final Path CONFIG_FILE_PATH = Paths.get("./config/discord_mc_chat/config.yml");

	/** Upper bound of {@link #PATH_PARTS_CACHE}. */
	private static final int PATH_PARTS_CACHE_MAX_ENTRIES = 256;

	/**
	 * Cache of configuration paths to their pre-split segments, so that the hot read path (about eleven
	 * boolean lookups per Discord-to-Minecraft message) does not re-split the same path string every time.
	 * <p>
	 * Capacity: {@value #PATH_PARTS_CACHE_MAX_ENTRIES} entries; the cache is dropped once the cap is reached
	 * and refilled on demand, which keeps the entry set bounded even for dynamically built paths.
	 * Invalidation: none needed — splitting a path never depends on the configuration content, so a cached
	 * entry cannot go stale across {@link #load()} calls.
	 */
	private static final Map<String, String[]> PATH_PARTS_CACHE = new ConcurrentHashMap<>();

	/**
	 * The loaded configuration tree. Written once by {@link #load()} and read by any thread afterwards,
	 * hence volatile so that other threads observe the fully parsed tree.
	 */
	private static volatile JsonNode config;

	private ConfigManager() {
	}

	public static boolean load() {
		String expectedMode = ModeManager.getMode();
		String configTemplatePath = "/config/config_" + expectedMode + ".yml";

		try {
			Files.createDirectories(CONFIG_FILE_PATH.getParent());
			if (!Files.exists(CONFIG_FILE_PATH) || Files.size(CONFIG_FILE_PATH) == 0) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.not_found"));
				LOGGER.info(I18nManager.getDmccTranslation("utils.config.config.creating", CONFIG_FILE_PATH));
				LOGGER.info(I18nManager.getDmccTranslation("utils.config.config.edit_prompt", CONFIG_FILE_PATH));

				try (InputStream inputStream = ConfigManager.class.getResourceAsStream(configTemplatePath)) {
					if (inputStream == null) {
						throw new IOException("Default config template not found: " + configTemplatePath);
					}

					String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

					// Replace the default language with the detected system language, so the generated
					// config file uses the user's system language if supported.
					template = template.replace("language: \"to_be_auto_replaced\"", StringUtils.format("language: \"{}\"", I18nManager.getLanguage()));

					if ("standalone".equals(expectedMode)) {
						String randomSecret = CryptUtils.generateRandomString(32);
						template = template.replace("shared_secret: \"to_be_auto_replaced\"", StringUtils.format("shared_secret: \"{}\"", randomSecret));
					}

					Files.writeString(CONFIG_FILE_PATH, template, StandardCharsets.UTF_8);
				}

				return false;
			}

			JsonNode userConfig;
			try (Reader reader = Files.newBufferedReader(CONFIG_FILE_PATH, StandardCharsets.UTF_8)) {
				userConfig = YAML_MAPPER.readTree(reader);
			}

			String configMode = userConfig.path("mode").asString();
			if (!expectedMode.equals(configMode)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.mode_mismatch"));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.mode_mismatch_detail", expectedMode, configMode));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.backup_prompt"));
				return false;
			}

			JsonNode templateConfig;
			try (InputStream templateStream = ConfigManager.class.getResourceAsStream(configTemplatePath)) {
				if (templateStream == null) {
					throw new IOException("Default config template not found: " + configTemplatePath);
				}
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

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

	public static JsonNode getConfigNode(String path) {
		JsonNode node = config;
		if (node == null) {
			// Config may not be loaded yet; a missing node is safer than a NullPointerException.
			return YAML_MAPPER.missingNode();
		}

		for (String part : pathParts(path)) {
			if (node == null || node.isMissingNode() || node.isNull()) {
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.path_not_found", path));
				return node;
			}
			node = node.path(part);
		}

		if (node == null || node.isMissingNode() || node.isNull()) {
			// The loop only checks the node it is about to descend into, so a path whose last segment is
			// missing would otherwise return silently.
			LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.path_not_found", path));
		}

		return node;
	}

	/**
	 * @return The path segments, identical to {@code path.split("\\.")}
	 */
	private static String[] pathParts(String path) {
		String[] cached = PATH_PARTS_CACHE.get(path);
		if (cached != null) {
			return cached;
		}

		String[] parts = path.split("\\.");
		if (PATH_PARTS_CACHE.size() >= PATH_PARTS_CACHE_MAX_ENTRIES) {
			PATH_PARTS_CACHE.clear();
		}
		PATH_PARTS_CACHE.put(path, parts);
		return parts;
	}

	/**
	 * @return The converted value, or null if the path is missing or null
	 */
	public static <T> T getValue(String path, Function<JsonNode, T> converter) {
		JsonNode node = getConfigNode(path);

		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		return converter.apply(node);
	}

	/**
	 * @return The string value at the specified path, or null if the path is missing or null
	 */
	public static String getString(String path) {
		return getValue(path, JsonNode::asString);
	}

	/**
	 * @return The string value at the specified path, or defaultValue if the path is missing or null
	 */
	public static String getString(String path, String defaultValue) {
		String value = getValue(path, JsonNode::asString);
		return value == null ? defaultValue : value;
	}

	/**
	 * @return The integer value at the specified path, or null if the path is missing or null
	 */
	public static Integer getInt(String path) {
		return getValue(path, JsonNode::asInt);
	}

	/**
	 * @return The integer value at the specified path, or defaultValue if the path is missing or null
	 */
	public static int getInt(String path, int defaultValue) {
		Integer value = getValue(path, JsonNode::asInt);
		return value == null ? defaultValue : value;
	}

	/**
	 * @return The double value at the specified path, or defaultValue if the path is missing or null
	 */
	public static Double getDouble(String path, double defaultValue) {
		Double value = getValue(path, JsonNode::asDouble);
		return value == null ? defaultValue : value;
	}

	/**
	 * @return The boolean value at the specified path, or null if the path is missing or null
	 */
	public static Boolean getBoolean(String path) {
		return getValue(path, JsonNode::asBoolean);
	}

	/**
	 * @return The boolean value at the specified path, or defaultValue if the path is missing or null
	 */
	public static boolean getBoolean(String path, boolean defaultValue) {
		Boolean value = getValue(path, JsonNode::asBoolean);
		return value == null ? defaultValue : value;
	}
}
