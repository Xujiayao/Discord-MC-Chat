package com.xujiayao.discord_mc_chat.utils.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
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
public class ModeManager {

	private static final Path MODE_FILE_PATH = Paths.get("./config/discord_mc_chat/mode.yml");
	private static final String MODE_TEMPLATE_PATH = "/config/mode.yml";

	private static String mode = "";

	/**
	 * Loads and validates the mode from mode.yml. If the file does not exist,
	 * it creates a default one and returns false to halt initialization.
	 *
	 * @return true if the mode was loaded and validated successfully, false otherwise.
	 */
	public static boolean load() {
		try {
			if (!IS_MINECRAFT_ENV) {
				mode = "standalone";
				LOGGER.info("Operating mode set to \"{}\"", mode);
				return true;
			}

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
				return false; // Halt initialization, requires user action
			}

			// Load the user's mode.yml
			JsonNode modeConfig = YAML_MAPPER.readTree(Files.newBufferedReader(MODE_FILE_PATH));

			// Load the template for validation
			JsonNode templateConfig;
			try (InputStream templateStream = ModeManager.class.getResourceAsStream(MODE_TEMPLATE_PATH)) {
				templateConfig = YAML_MAPPER.readTree(templateStream);
			}

			// Validate the mode file
			if (!YamlUtils.validate(modeConfig, templateConfig, MODE_FILE_PATH, true)) {
				LOGGER.error("Validation of mode.yml failed. Please correct the errors mentioned above.");
				return false;
			}

			String loadedMode = modeConfig.path("mode").asText(null);

			if (loadedMode == null || loadedMode.trim().isEmpty() || "your_option_here".equals(loadedMode)) {
				LOGGER.error("No mode selected in \"{}\"", MODE_FILE_PATH);
				LOGGER.error("Please edit the file to select an operating mode, then run \"/dmcc reload\"");
				return false;
			}

			if (!"single_server".equals(loadedMode) && !"multi_server_client".equals(loadedMode)) {
				LOGGER.error("Invalid mode \"{}\" selected in \"{}\".", loadedMode, MODE_FILE_PATH);
				LOGGER.error("Available modes are: single_server, multi_server_client");
				return false;
			}

			LOGGER.info("Operating mode set to \"{}\"", loadedMode);
			mode = loadedMode;
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to load or create mode.yml", e);
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
