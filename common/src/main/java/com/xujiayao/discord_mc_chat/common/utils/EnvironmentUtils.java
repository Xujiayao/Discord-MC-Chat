package com.xujiayao.discord_mc_chat.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xujiayao.discord_mc_chat.common.utils.config.ConfigManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static com.xujiayao.discord_mc_chat.common.DMCC.IS_MINECRAFT_ENV;

/**
 * Environment utility class.
 *
 * @author Xujiayao
 */
public class EnvironmentUtils {

	/**
	 * Check if running in a Minecraft environment (Fabric or NeoForge)
	 *
	 * @return true if running in a Minecraft environment, false otherwise
	 */
	public static boolean isMinecraftEnvironment() {
		// Fabric
		try {
			Class.forName("net.fabricmc.loader.api.FabricLoader");
			return true;
		} catch (ClassNotFoundException ignored) {
		}

		// NeoForge
		try {
			Class.forName("net.neoforged.fml.loading.FMLLoader");
			return true;
		} catch (ClassNotFoundException ignored) {
		}

		return false;
	}

	/**
	 * Get DMCC version from resource file "fabric.mod.json"
	 *
	 * @return DMCC version in string
	 */
	public static String getVersionByResource() {
		InputStream stream = EnvironmentUtils.class.getResourceAsStream("/fabric.mod.json");
		if (stream == null) {
			throw new RuntimeException("File \"fabric.mod.json\" not found");
		}

		try (Reader reader = new InputStreamReader(stream)) {
			JsonNode root = new ObjectMapper().readTree(reader);
			return root.get("version").asText();
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
			// Fabric
			try {
				Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
				Object loaderInstance = fabricLoaderClass.getMethod("getInstance").invoke(null);
				return (String) loaderInstance.getClass().getMethod("getRawGameVersion").invoke(loaderInstance);
			} catch (Exception ignored) {
			}

			// NeoForge
			try {
				Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
				Object versionInfo = fmlLoaderClass.getMethod("versionInfo").invoke(null);
				return (String) versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
			} catch (Exception ignored) {
			}
		}

		// Standalone or error
		return ConfigManager.getString("multi_server.minecraft_version", "error");
	}
}
