package com.xujiayao.discord_mc_chat.config;

import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Configuration manager for DMCC.
 * Handles loading, validation, and access to configuration values.
 * <p>
 * The operating mode is read from {@code config.yml} itself. When the file does not exist yet,
 * DMCC creates it from the template that matches the environment default: {@code single_server}
 * inside Minecraft, {@code standalone} for the standalone JAR. There is no separate {@code mode.yml};
 * users who need {@code multi_server_client} either set the {@code mode} key by hand (Standalone DMCC
 * and the generated templates document how) or use the config generator provided by the documentation site.
 *
 * @author Xujiayao
 */
public final class ConfigManager {

	private static final Path CONFIG_FILE_PATH = Paths.get("./config/discord_mc_chat/config.yml");

	/**
	 * Operating mode for a standalone DMCC process (no Minecraft attached).
	 */
	public static final String MODE_STANDALONE = "standalone";

	/**
	 * Operating mode where a Minecraft server also hosts the DMCC server.
	 */
	public static final String MODE_SINGLE_SERVER = "single_server";

	/**
	 * Operating mode where a Minecraft server joins an existing DMCC standalone server.
	 */
	public static final String MODE_MULTI_SERVER_CLIENT = "multi_server_client";

	private static JsonNode config;
	private static String mode = "";

	private ConfigManager() {
	}

	/**
	 * Loads the configuration file, creating it from the environment's default template on first run.
	 *
	 * @return true if the config was loaded and validated successfully, false otherwise.
	 */
	public static boolean load() {
		String defaultMode = getDefaultMode();

		try {
			// Create directories if they do not exist
			Files.createDirectories(CONFIG_FILE_PATH.getParent());

			// If config.yml does not exist or is empty, create it from the default template for this environment
			if (!Files.exists(CONFIG_FILE_PATH) || Files.size(CONFIG_FILE_PATH) == 0) {
				return createDefaultConfig(defaultMode);
			}

			// Load the user's config.yml
			JsonNode userConfig = YAML_MAPPER.readTree(Files.newBufferedReader(CONFIG_FILE_PATH, StandardCharsets.UTF_8));

			// Determine the operating mode from the config itself, falling back to the environment default
			String configMode = userConfig.path("mode").asString("");

			if (configMode.isBlank()) {
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.mode.missing", CONFIG_FILE_PATH, defaultMode));
				configMode = defaultMode;
			}

			if (!isValidMode(configMode)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.invalid_selection", configMode, CONFIG_FILE_PATH));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.available_modes"));
				return false;
			}

			// The environment dictates which modes make sense
			if (IS_MINECRAFT_ENV == MODE_STANDALONE.equals(configMode)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.env_mismatch",
						configMode, IS_MINECRAFT_ENV ? "Minecraft" : "standalone"));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.env_mismatch_detail"));
				return false;
			}

			// Validate the user's config against the template of the selected mode
			JsonNode templateConfig = readTemplate("/config/config_" + configMode + ".yml");

			if (!YamlUtils.validate(userConfig, templateConfig, true)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.validation_failed"));
				return false;
			}

			ConfigManager.config = userConfig;
			ConfigManager.mode = configMode;
			LOGGER.info(I18nManager.getDmccTranslation("utils.config.mode.set", configMode));
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.config.config.load_failed"), e);
			return false;
		}
	}

	/**
	 * Gets the operating mode of the current run.
	 *
	 * @return One of {@link #MODE_SINGLE_SERVER}, {@link #MODE_MULTI_SERVER_CLIENT} or {@link #MODE_STANDALONE}.
	 */
	public static String getMode() {
		return mode;
	}

	/**
	 * Gets the mode this environment uses when the user has not chosen one.
	 *
	 * @return {@link #MODE_SINGLE_SERVER} inside Minecraft, {@link #MODE_STANDALONE} otherwise.
	 */
	public static String getDefaultMode() {
		return IS_MINECRAFT_ENV ? MODE_SINGLE_SERVER : MODE_STANDALONE;
	}

	/**
	 * Checks whether the given value is a mode DMCC supports.
	 *
	 * @param value Mode value to check.
	 * @return true when the value names a supported mode.
	 */
	public static boolean isValidMode(String value) {
		return MODE_SINGLE_SERVER.equals(value)
				|| MODE_MULTI_SERVER_CLIENT.equals(value)
				|| MODE_STANDALONE.equals(value);
	}

	/**
	 * Writes a first-run config.yml from the template of the given mode and explains what to do next.
	 *
	 * @param mode Mode whose template should be generated.
	 * @return always false, because the user has to edit the generated file before DMCC can run.
	 */
	private static boolean createDefaultConfig(String mode) throws IOException {
		Path absolutePath = CONFIG_FILE_PATH.toAbsolutePath().normalize();

		LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.not_found"));
		LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.creating", absolutePath));

		String template = new String(readTemplateBytes("/config/config_" + mode + ".yml"), StandardCharsets.UTF_8);

		// Replace the default language with the detected system language.
		// This ensures the generated config file uses the user's system language if supported.
		template = template.replace("language: \"to_be_auto_replaced\"", StringUtils.format("language: \"{}\"", I18nManager.getLanguage()));

		// If in standalone mode, generate a secure random shared secret
		if (MODE_STANDALONE.equals(mode)) {
			String randomSecret = CryptUtils.generateRandomString(32);
			template = template.replace("shared_secret: \"to_be_auto_replaced\"", StringUtils.format("shared_secret: \"{}\"", randomSecret));
		}

		Files.writeString(CONFIG_FILE_PATH, template, StandardCharsets.UTF_8);

		// Logged one line per message: the logger escapes line breaks on purpose (to keep log entries
		// single-line), so multi-line guidance has to be emitted as separate log calls.
		LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.first_run_guide", absolutePath));
		LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.first_run_steps"));
		LOGGER.warn(I18nManager.getDmccTranslation("utils.config.config.first_run_apply"));

		return false;
	}

	/**
	 * Reads a bundled template as bytes.
	 *
	 * @param resourcePath Absolute resource path of the template.
	 * @return The template bytes.
	 * @throws IOException If the template is missing.
	 */
	private static byte[] readTemplateBytes(String resourcePath) throws IOException {
		try (InputStream inputStream = ConfigManager.class.getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				throw new IOException("Default config template not found: " + resourcePath);
			}
			return inputStream.readAllBytes();
		}
	}

	/**
	 * Reads a bundled template as a YAML tree.
	 *
	 * @param resourcePath Absolute resource path of the template.
	 * @return The parsed template.
	 * @throws IOException If the template is missing or invalid.
	 */
	private static JsonNode readTemplate(String resourcePath) throws IOException {
		try (InputStream inputStream = ConfigManager.class.getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				throw new IOException("Default config template not found: " + resourcePath);
			}
			return YAML_MAPPER.readTree(inputStream);
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
		return getValue(path, JsonNode::asString);
	}

	/**
	 * Gets a configuration value as a string, with a default value if not found.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path is not found
	 * @return The string value at the specified path, or null if not found
	 */
	public static String getString(String path, String defaultValue) {
		String value = getValue(path, JsonNode::asString);
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
	 * Gets a configuration value as a double, with a default value if not found.
	 *
	 * @param path         The path to the configuration value
	 * @param defaultValue The default value to return if the path is not found
	 * @return The double value at the specified path
	 */
	public static Double getDouble(String path, double defaultValue) {
		Double value = getValue(path, JsonNode::asDouble);
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
