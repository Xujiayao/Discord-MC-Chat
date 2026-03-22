package com.xujiayao.discord_mc_chat.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Manages the mode.yml file to determine and provide the operating mode of DMCC.
 *
 * @author Xujiayao
 */
public final class ModeManager {

	private static final Path MODE_FILE_PATH = Paths.get("./config/discord_mc_chat/mode.yml");
	private static final String MODE_TEMPLATE_PATH = "/config/mode.yml";

	private static String mode = "";

	private ModeManager() {
	}

	/**
	 * Loads and validates the mode from mode.yml. If the file does not exist,
	 * it creates a default one and returns false to halt initialization.
	 *
	 * @return true if the mode was loaded and validated successfully, false otherwise.
	 */
	public static boolean load() {
		if (!IS_MINECRAFT_ENV) {
			mode = "standalone";
			LOGGER.info(I18nManager.getDmccTranslation("utils.config.mode.set", mode));
			return true;
		}

		try {
			// Create directories if they do not exist
			Files.createDirectories(MODE_FILE_PATH.getParent());

			// If mode.yml does not exist, create it from the template
			if (!Files.exists(MODE_FILE_PATH) || Files.size(MODE_FILE_PATH) == 0) {
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.mode.not_found"));
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.mode.creating", MODE_FILE_PATH));
				LOGGER.warn(I18nManager.getDmccTranslation("utils.config.mode.edit_prompt", MODE_FILE_PATH));

				try (InputStream inputStream = ModeManager.class.getResourceAsStream(MODE_TEMPLATE_PATH)) {
					if (inputStream == null) {
						throw new IOException("Default mode template not found: " + MODE_TEMPLATE_PATH);
					}
					Files.copy(inputStream, MODE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
				}

				return false; // Halt initialization, requires user action
			}

			// Load the user's mode.yml
			JsonNode userModeConfig = YAML_MAPPER.readTree(Files.newBufferedReader(MODE_FILE_PATH, StandardCharsets.UTF_8));

			// Load the template for validation
			JsonNode templateModeConfig;
			try (InputStream templateStream = ModeManager.class.getResourceAsStream(MODE_TEMPLATE_PATH)) {
				templateModeConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate the mode file
			if (!YamlUtils.validate(userModeConfig, templateModeConfig, true)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.validation_failed"));
				return false;
			}

			String loadedMode = userModeConfig.path("mode").asText();

			if (!"single_server".equals(loadedMode) && !"multi_server_client".equals(loadedMode)) {
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.invalid_selection", loadedMode, MODE_FILE_PATH));
				LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.available_modes"));
				return false;
			}

			LOGGER.info(I18nManager.getDmccTranslation("utils.config.mode.set", loadedMode));
			mode = loadedMode;
			return true;
		} catch (IOException e) {
			LOGGER.error(I18nManager.getDmccTranslation("utils.config.mode.load_failed"), e);
			return false;
		}
	}

	/**
	 * Gets the currently active operating mode.
	 *
	 * @return The current mode as a string, or null if not loaded.
	 */
	public static String getMode() {
		return mode;
	}
}
