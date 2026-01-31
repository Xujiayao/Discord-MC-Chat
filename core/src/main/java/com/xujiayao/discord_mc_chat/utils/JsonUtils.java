package com.xujiayao.discord_mc_chat.utils;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;

/**
 * JSON utility class that wraps Jackson operations.
 *
 * @author Xujiayao
 */
public class JsonUtils {

	/**
	 * Converts a JSON String to a Map of String to String.
	 *
	 * @param json The JSON String to convert
	 * @return The converted Map
	 * @throws IOException If parsing fails
	 */
	public static Map<String, String> toStringMap(String json) throws IOException {
		return JSON_MAPPER.convertValue(JSON_MAPPER.readTree(json), new TypeReference<>() {
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
		return JSON_MAPPER.convertValue(JSON_MAPPER.readTree(reader), new TypeReference<>() {
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
		return JSON_MAPPER.readValue(inputStream, new TypeReference<>() {
		});
	}
}
