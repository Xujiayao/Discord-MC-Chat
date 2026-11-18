package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.Constants;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Helpers for turning a Minecraft profile's signed `textures` property into the texture hash accepted by
 * avatar services such as mc-heads.net.
 *
 * @author Xujiayao
 */
public final class SkinUtils {

	private static final String TEXTURE_URL_PREFIX = "http://textures.minecraft.net/texture/";
	private static final String TEXTURE_URL_PREFIX_SECURE = "https://textures.minecraft.net/texture/";

	private SkinUtils() {
	}

	/**
	 * Decodes the Base64 `textures` property of a GameProfile and extracts the skin texture hash.
	 *
	 * @return The texture hash, or an empty string when the property is absent/malformed or has no skin
	 */
	public static String extractTextureHash(String texturesPropertyValue) {
		if (texturesPropertyValue == null || texturesPropertyValue.isBlank()) {
			return "";
		}

		try {
			byte[] decoded = Base64.getDecoder().decode(texturesPropertyValue);
			JsonNode root = Constants.JSON_MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
			JsonNode urlNode = root.path("textures").path("SKIN").path("url");
			if (!urlNode.isString()) {
				return "";
			}
			return stripTextureUrlPrefix(urlNode.stringValue());
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String stripTextureUrlPrefix(String url) {
		if (url.startsWith(TEXTURE_URL_PREFIX)) {
			return url.substring(TEXTURE_URL_PREFIX.length());
		}
		if (url.startsWith(TEXTURE_URL_PREFIX_SECURE)) {
			return url.substring(TEXTURE_URL_PREFIX_SECURE.length());
		}
		return "";
	}
}
