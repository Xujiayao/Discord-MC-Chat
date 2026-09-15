package com.xujiayao.discord_mc_chat.utils;

import java.util.regex.Pattern;

/**
 * String utility class.
 *
 * @author Xujiayao
 */
public final class StringUtils {

	/**
	 * Indexed printf-style placeholder, e.g. {@code %1$s}.
	 */
	private static final Pattern INDEXED_PRINTF_PLACEHOLDER = Pattern.compile("%\\d+\\$s");

	private StringUtils() {
	}

	/**
	 * Escape special characters in strings.
	 *
	 * @param s String to escape
	 * @return Escaped string
	 */
	public static String escape(String s) {
		// Every log line goes through here, so scan once and only build a new string when needed.
		boolean needsEscaping = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\t' || c == '\b' || c == '\n' || c == '\r' || c == '\f') {
				needsEscaping = true;
				break;
			}
		}
		if (!needsEscaping) {
			return s;
		}

		return s.replace("\t", "\\t")
				.replace("\b", "\\b")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\f", "\\f");
	}

	/**
	 * Formats a string with placeholders.
	 * <p>
	 * Supports multiple placeholder styles:
	 * <ul>
	 *   <li>DMCC-style Sequential <code>{}</code> and indexed <code>{n}</code> placeholders</li>
	 *   <li>Minecraft-style sequential <code>%s</code> and indexed <code>%n$s</code> placeholders</li>
	 * </ul>
	 * Note: Do not mix different styles in the same string.
	 *
	 * @param str  String with placeholders
	 * @param args Arguments to replace the placeholders
	 * @return String with placeholders replaced
	 */
	public static String format(String str, Object... args) {
		if (str == null || args == null || args.length == 0) {
			return str;
		}

		// Check if the string uses indexed DMCC-style placeholders (e.g., "{0}", "{1}")
		if (str.contains("{0}")) {
			for (int i = 0; i < args.length; i++) {
				String target = "{" + i + "}";
				String replacement = args[i] == null ? "null" : args[i].toString();
				str = str.replace(target, replacement);
			}
			return str;
		}

		// Check if the string uses sequential DMCC-style "{}" placeholders
		if (str.contains("{}")) {
			StringBuilder sb = new StringBuilder(str.length());
			int searchStart = 0;
			int argIndex = 0;

			while (argIndex < args.length) {
				int placeholderIndex = str.indexOf("{}", searchStart);
				if (placeholderIndex == -1) {
					break;
				}

				sb.append(str, searchStart, placeholderIndex);
				sb.append(args[argIndex] == null ? "null" : args[argIndex].toString());

				searchStart = placeholderIndex + 2;
				argIndex++;
			}

			sb.append(str.substring(searchStart));
			return sb.toString();
		}

		// Check if the string uses Minecraft-style placeholders (%s or %n$s)
		if (str.contains("%s") || INDEXED_PRINTF_PLACEHOLDER.matcher(str).find()) {
			// Use String.format for standard printf-style formatting
			return String.format(str, args);
		}

		// No valid placeholders found, return original string
		return str;
	}
}
