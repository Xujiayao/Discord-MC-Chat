package com.xujiayao.discord_mc_chat.common.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.common.utils.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;
import static com.xujiayao.discord_mc_chat.common.DMCC.YAML_MAPPER;

/**
 * Manages the mode.yml file to determine the operating mode of DMCC.
 *
 * @author Xujiayao
 */
public class ModeManager {

	private static final Path MODE_FILE_PATH = Paths.get("./config/discord_mc_chat/mode.yml");
	private static final String MODE_TEMPLATE_PATH = "/config/mode.yml";

	/**
	 * Loads and validates the mode from mode.yml. If the file does not exist,
	 * it creates a default one and returns false to halt initialization.
	 *
	 * @return The loaded mode as a string, or null if loading fails or requires user action.
	 */
	public static String load() {
		try {
			// Create directories if they do not exist
			Files.createDirectories(MODE_FILE_PATH.getParent());

			// If mode.yml does not exist, create it from the template
			if (!Files.exists(MODE_FILE_PATH) || Files.size(MODE_FILE_PATH) == 0) {
				LOGGER.warn("Mode configuration file not found or is empty. Creating a default one.");
				LOGGER.info("Please edit \"{}\" to select an operating mode, then run \"/dmcc reload\"", MODE_FILE_PATH);
				try (InputStream inputStream = ModeManager.class.getResourceAsStream(MODE_TEMPLATE_PATH)) {
					if (inputStream == null) {
						throw new IOException("Default mode template not found: " + MODE_TEMPLATE_PATH);
					}
					Files.copy(inputStream, MODE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
				}
				return null; // Halt initialization, requires user action
			}

			// Load the user's mode.yml
			JsonNode modeConfig = YAML_MAPPER.readTree(Files.newBufferedReader(MODE_FILE_PATH));

			// Load the template for validation
			JsonNode templateConfig;
			try (InputStream templateStream = ModeManager.class.getResourceAsStream(MODE_TEMPLATE_PATH)) {
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate the mode file
			if (!YamlUtils.validate(modeConfig, templateConfig, MODE_FILE_PATH, false)) {
				LOGGER.error("Validation of mode.yml failed. Please correct the errors.");
				return null;
			}

			String loadedMode = modeConfig.path("mode").asText(null);

			if (loadedMode == null || loadedMode.trim().isEmpty() || loadedMode.startsWith("#")) {
				LOGGER.error("No mode selected in \"{}\". Please uncomment one of the mode options.", MODE_FILE_PATH);
				return null;
			}

			if (!"single_server".equals(loadedMode) && !"multi_server_client".equals(loadedMode)) {
				LOGGER.error("Invalid mode \"{}\" selected in \"{}\".", loadedMode, MODE_FILE_PATH);
				LOGGER.error("Available modes are: single_server, multi_server_client");
				return null;
			}

			LOGGER.info("Operating mode set to \"{}\"", loadedMode);
			return loadedMode;

		} catch (IOException e) {
			LOGGER.error("Failed to load or create mode.yml", e);
			return null;
		}
	}
}
