package com.xujiayao.discord_mc_chat.common.utils;

/**
 * String utility class.
 *
 * @author Xujiayao
 */
public class StringUtils {

	/**
	 * Escape special characters in strings.
	 *
	 * @param s String to escape
	 * @return Escaped string
	 */
	public static String escape(String s) {
		return s.replace("\t", "\\t")
				.replace("\b", "\\b")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\f", "\\f");
	}

	/**
	 * Simple {} placeholder replacement.
	 *
	 * @param str  String with {} placeholders
	 * @param args Arguments to replace the placeholders
	 * @return String with placeholders replaced
	 */
	public static String format(String str, Object... args) {
		for (Object arg : args) {
			str = str.replaceFirst("\\{}", arg == null ? "null" : arg.toString().replace("\\", "\\\\"));
		}
		return str;
	}
}
