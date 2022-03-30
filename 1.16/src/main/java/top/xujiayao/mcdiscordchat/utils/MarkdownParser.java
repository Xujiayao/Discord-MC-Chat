package top.xujiayao.mcdiscordchat.utils;

import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
public class MarkdownParser {

	public static String parseMarkdown(String message) {
		message = replaceWith(message, "(?<!\\\\)\\*\\*", Formatting.BOLD.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)\\*", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)__", Formatting.UNDERLINE.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)_", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)~~", Formatting.STRIKETHROUGH.toString(), Formatting.RESET.toString());

		message = message.replaceAll("\\\\\\*", "*").replaceAll("\\\\_", "_").replaceAll("\\\\~", "~");

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
