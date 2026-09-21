package com.xujiayao.discord_mc_chat.utils;

import java.util.regex.Pattern;

public final class StringUtils {

	/**
	 * Detects Minecraft-style indexed placeholders ({@code %1$s}).
	 * <p>
	 * Used with {@link java.util.regex.Matcher#matches()} and not with {@code find()}: the previous code was
	 * {@code str.matches(".*%\\d+\\$s.*")} and {@code .} does not match line terminators, so a placeholder that
	 * follows a newline must stay undetected. {@code matches()} is the exact equivalent of {@code String.matches}.
	 */
	private static final Pattern INDEXED_PRINTF_PLACEHOLDER = Pattern.compile(".*%\\d+\\$s.*");

	private StringUtils() {
	}

	public static String escape(String s) {
		return s.replace("\t", "\\t")
				.replace("\b", "\\b")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\f", "\\f");
	}

	/**
	 * Formats a string with placeholders. Supports DMCC-style sequential <code>{}</code> and indexed
	 * <code>{n}</code> placeholders, plus Minecraft-style sequential <code>%s</code> and indexed
	 * <code>%n$s</code> placeholders.
	 * <p>
	 * Do not mix different styles in the same string.
	 */
	public static String format(String str, Object... args) {
		if (str == null || args == null || args.length == 0) {
			return str;
		}

		if (str.contains("{0}")) {
			for (int i = 0; i < args.length; i++) {
				String target = "{" + i + "}";
				String replacement = args[i] == null ? "null" : args[i].toString();
				str = str.replace(target, replacement);
			}
			return str;
		}

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

		if (str.contains("%s") || INDEXED_PRINTF_PLACEHOLDER.matcher(str).matches()) {
			return String.format(str, args);
		}

		return str;
	}
}
