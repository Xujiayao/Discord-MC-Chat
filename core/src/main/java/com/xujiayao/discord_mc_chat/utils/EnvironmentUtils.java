package com.xujiayao.discord_mc_chat.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Environment utility class.
 *
 * @author Xujiayao
 */
public final class EnvironmentUtils {

	private EnvironmentUtils() {
	}

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
	 * Gets the Minecraft version using reflection.
	 * Should only be called if running in a Minecraft environment.
	 */
	public static String getMinecraftVersion() {
		try {
			// SharedConstants.getCurrentVersion().name()
			Class<?> sharedConstantsClass = Class.forName("net.minecraft.SharedConstants");
			Object worldVersionObject = sharedConstantsClass.getMethod("getCurrentVersion").invoke(null);
			return (String) worldVersionObject.getClass().getMethod("name").invoke(worldVersionObject);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get Minecraft version", e);
		}
	}

	/**
	 * Gets the DMCC version from the {@code dmcc_version.txt} resource, whose {@code ${mod_version}}
	 * placeholder is expanded at build time.
	 *
	 * @return The DMCC version as a string.
	 */
	public static String getDmccVersion() {
		String resourcePath = "/dmcc_version.txt";

		try (InputStream versionStream = EnvironmentUtils.class.getResourceAsStream(resourcePath)) {
			if (versionStream == null) {
				throw new RuntimeException("File \"" + resourcePath + "\" not found");
			}

			String version = new String(versionStream.readAllBytes(), StandardCharsets.UTF_8).trim();

			if (version.isBlank()) {
				throw new RuntimeException("DMCC version resource is empty");
			}

			return version;
		} catch (IOException e) {
			throw new RuntimeException("Failed to identify DMCC version", e);
		}
	}
}
