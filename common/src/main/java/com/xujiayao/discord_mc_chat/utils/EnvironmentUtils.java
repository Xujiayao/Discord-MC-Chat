package com.xujiayao.discord_mc_chat.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;

import java.io.IOException;
import java.io.InputStream;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * Environment utility class.
 *
 * @author Xujiayao
 */
public class EnvironmentUtils {

	/**
	 * Check if running in a Minecraft environment.
	 *
	 * @return true if running in a Minecraft environment, false otherwise
	 */
	public static boolean isMinecraftEnvironment() {
		try {
			Class.forName("net.minecraft.SharedConstants");
			return true;
		} catch (ClassNotFoundException ignored) {
		}

		return false;
	}

	/**
	 * Gets the DMCC version from the template "mode.yml" file in resources.
	 *
	 * @return The DMCC version as a string.
	 */
	public static String getDmccVersion() {
		String filePath = "/config/mode.yml";

		// Load the template from resources
		try (InputStream templateStream = EnvironmentUtils.class.getResourceAsStream(filePath)) {
			if (templateStream == null) {
				throw new RuntimeException("File \"" + filePath + "\" not found");
			}
			JsonNode templateConfig = YAML_MAPPER.readTree(templateStream);

			// Extract version field
			String version = templateConfig.path("version").asText("");

			if (version.isBlank()) {
				throw new RuntimeException("Version field not found in template configuration");
			}

			return version;
		} catch (IOException e) {
			throw new RuntimeException("Failed to identify DMCC version", e);
		}
	}

	/**
	 * Determines the current Minecraft version based on the environment.
	 *
	 * @return The Minecraft version string.
	 */
	public static String getMinecraftVersion() {
		if (IS_MINECRAFT_ENV) {
			// Minecraft Environment
			try {
				Class<?> sharedConstantsClass = Class.forName("net.minecraft.SharedConstants");
				Object worldVersionObject = sharedConstantsClass.getMethod("getCurrentVersion").invoke(null);
				return (String) worldVersionObject.getClass().getMethod("name").invoke(worldVersionObject);
			} catch (Exception e) {
				throw new RuntimeException("Failed to get Minecraft version in Minecraft environment", e);
			}
		}

		// Standalone
		return ConfigManager.getString("multi_server.minecraft_version");
	}
}
