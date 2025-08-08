package com.xujiayao.discord_mc_chat.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Utils {

	public static String getVersion() {
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
