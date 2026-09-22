package com.xujiayao.discord_mc_chat.utils;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;

import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * @author Xujiayao
 */
public final class EnvironmentUtils {

	/**
	 * Cached result of the Minecraft classpath probe. Capacity: 1 entry; invalidation: none, because the
	 * classloader answering {@link Class#forName(String)} for this class cannot change while the JVM runs, so
	 * the first answer is authoritative. This also keeps the per-log-line {@code isMinecraftEnvironment()}
	 * calls free of reflection.
	 */
	private static volatile Boolean minecraftEnvironment;

	/**
	 * Cached result of the reflective Minecraft version lookup. Capacity: 1 entry; invalidation: none, because
	 * the runtime classpath cannot change while the JVM runs. A failed lookup is cached as well (in
	 * {@link #minecraftVersionFailure}), so a broken environment is not re-resolved through reflection on
	 * every call.
	 */
	private static volatile String minecraftVersion;
	private static volatile Throwable minecraftVersionFailure;

	private EnvironmentUtils() {
	}

	public static boolean isMinecraftEnvironment() {
		Boolean cached = minecraftEnvironment;
		if (cached != null) {
			return cached;
		}

		boolean result = false;
		try {
			Class.forName("net.minecraft.SharedConstants");
			result = true;
		} catch (ClassNotFoundException ignored) {
		}

		minecraftEnvironment = result;
		return result;
	}

	/**
	 * Should only be called if running in a Minecraft environment.
	 */
	public static String getMinecraftVersion() {
		Throwable failure = minecraftVersionFailure;
		if (failure != null) {
			// The lookup already failed once; rethrow it instead of repeating the reflection.
			throw new RuntimeException("Failed to get Minecraft version", failure);
		}

		String cached = minecraftVersion;
		if (cached != null) {
			return cached;
		}

		try {
			// SharedConstants.getCurrentVersion().name()
			Class<?> sharedConstantsClass = Class.forName("net.minecraft.SharedConstants");
			Object worldVersionObject = sharedConstantsClass.getMethod("getCurrentVersion").invoke(null);
			String version = (String) worldVersionObject.getClass().getMethod("name").invoke(worldVersionObject);
			minecraftVersion = version;
			return version;
		} catch (Exception e) {
			minecraftVersionFailure = e;
			throw new RuntimeException("Failed to get Minecraft version", e);
		}
	}

	public static String getDmccVersion() {
		String filePath = "/config/mode.yml";

		try (InputStream templateStream = EnvironmentUtils.class.getResourceAsStream(filePath)) {
			if (templateStream == null) {
				throw new RuntimeException("File \"" + filePath + "\" not found");
			}
			JsonNode templateConfig = YAML_MAPPER.readTree(templateStream);

			String version = templateConfig.path("version").asString();

			if (version.isBlank()) {
				throw new RuntimeException("Version field not found in template configuration");
			}

			return version;
		} catch (IOException e) {
			throw new RuntimeException("Failed to identify DMCC version", e);
		}
	}
}
