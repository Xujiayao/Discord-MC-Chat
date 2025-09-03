package com.xujiayao.discord_mc_chat.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Utility class
 *
 * @author Xujiayao
 */
public class Utils {

	/**
	 * Check if running in a Minecraft environment (Fabric or NeoForge)
	 *
	 * @return True if running in a Minecraft environment, false otherwise
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
		InputStream stream = Utils.class.getResourceAsStream("/fabric.mod.json");
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
}
