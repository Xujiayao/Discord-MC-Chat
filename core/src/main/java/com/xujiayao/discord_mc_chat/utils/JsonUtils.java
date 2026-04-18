package com.xujiayao.discord_mc_chat.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.YAML_MAPPER;

/**
 * JSON utility class that wraps Jackson operations.
 * <p>
 * Uses the YAML_MAPPER from Constants for JSON processing to support # comments in JSON (BlazeAndCaves).
 * Leading tab indentation is normalized before parsing to tolerate legacy translation resources.
 *
 * @author Xujiayao
 */
public final class JsonUtils {

	private JsonUtils() {
	}

	/**
	 * Converts a JSON String to a Map of String to String.
	 *
	 * @param json The JSON String to convert
	 * @return The converted Map
	 * @throws IOException If parsing fails
	 */
	public static Map<String, String> toStringMap(String json) throws IOException {
		return YAML_MAPPER.readValue(normalizeLeadingTabs(json), new TypeReference<>() {
		});
	}

	/**
	 * Converts JSON from a Reader to a Map of String to String.
	 *
	 * @param reader The Reader to read JSON from
	 * @return The converted Map
	 * @throws IOException If reading or parsing fails
	 */
	public static Map<String, String> toStringMap(Reader reader) throws IOException {
		return YAML_MAPPER.readValue(normalizeLeadingTabs(readAll(reader)), new TypeReference<>() {
		});
	}

	/**
	 * Parses JSON from an InputStream into a Map of String to String.
	 *
	 * @param inputStream The InputStream to parse from
	 * @return The parsed Map
	 * @throws IOException If parsing fails
	 */
	public static Map<String, String> toStringMap(InputStream inputStream) throws IOException {
		return YAML_MAPPER.readValue(normalizeLeadingTabs(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)), new TypeReference<>() {
		});
	}

	private static String readAll(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[4096];
		int length;

		while ((length = reader.read(buffer)) != -1) {
			builder.append(buffer, 0, length);
		}

		return builder.toString();
	}

	private static String normalizeLeadingTabs(String input) {
		StringBuilder normalized = new StringBuilder(input.length());
		boolean lineStart = true;

		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);

			if (lineStart) {
				if (ch == '\t') {
					normalized.append("  ");
					continue;
				}

				if (ch == ' ') {
					normalized.append(ch);
					continue;
				}
			}

			normalized.append(ch);
			lineStart = ch == '\n' || ch == '\r';
		}

		return normalized.toString();
	}

	/**
	 * Reads a specific statistic from a player's stats JSON file.
	 *
	 * @param path The path to the JSON file
	 * @param type The stat type (e.g., "minecraft:custom")
	 * @param stat The stat name (e.g., "minecraft:deaths")
	 * @return The stat value, or 0 if not found
	 */
	public static int getStat(Path path, String type, String stat) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonNode root = JSON_MAPPER.readTree(reader);
			JsonNode typeNode = root.path("stats").path(type);
			if (!typeNode.isMissingNode() && typeNode.has(stat)) {
				return typeNode.path(stat).asInt();
			}
		} catch (Exception ignored) {
		}
		return 0;
	}
}
