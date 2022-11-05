/*
This file was obtained from BRForgers/DisFabric, licensed under the Mozilla Public License 2.0 (MPL-2.0).
Author: allanf181 (Allan Fernando)
Link to the original source: https://github.com/BRForgers/DisFabric (/src/main/java/br/com/brforgers/mods/disfabric/utils/MarkdownParser.java)
Link to the license: https://raw.githubusercontent.com/BRForgers/DisFabric/master/LICENSE
 */

package top.xujiayao.mcdiscordchat.utils;

import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author allanf181
 * @author Xujiayao
 */
public class MarkdownParser {

	public static String parseMarkdown(String message) {
		message = replaceWith(message, "(?<!\\\\)\\*\\*", Formatting.BOLD.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)\\*", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)__", Formatting.UNDERLINE.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)_", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)~~", Formatting.STRIKETHROUGH.toString(), Formatting.RESET.toString());

		message = message.replace("\\\\*", "*").replace("\\\\_", "_").replace("\\\\~", "~");

		message = message.replace("\"", "\\\"");

		return message;
	}

	private static String replaceWith(String message, String quot, String pre, String suf) {
		String part = message;

		for (String str : getMatches(message, quot + "(.+?)" + quot)) {
			part = part.replaceFirst(quot + Pattern.quote(str) + quot, pre + str + suf);
		}

		return part;
	}

	private static List<String> getMatches(String string, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(string);
		List<String> matches = new ArrayList<>();

		while (matcher.find()) {
			matches.add(matcher.group(1));
		}

		return matches;
	}
}
