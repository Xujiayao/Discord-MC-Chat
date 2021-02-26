package top.xujiayao.mcDiscordChat.utils;

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
		String translated = message;

		translated = replaceWith(translated, "(?<!\\\\)\\*\\*", Formatting.BOLD.toString(), Formatting.RESET.toString());
		translated = replaceWith(translated, "(?<!\\\\)\\*", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		translated = replaceWith(translated, "(?<!\\\\)__", Formatting.UNDERLINE.toString(), Formatting.RESET.toString());
		translated = replaceWith(translated, "(?<!\\\\)_", Formatting.ITALIC.toString(), Formatting.RESET.toString());
		translated = replaceWith(translated, "(?<!\\\\)~~", Formatting.STRIKETHROUGH.toString(), Formatting.RESET.toString());

		translated = translated.replaceAll("\\*", "*").replaceAll("\\_", "_").replaceAll("\\~", "~");
		translated = translated.replaceAll(Formatting.ITALIC.toString() + "(ツ)" + Formatting.RESET.toString(), "_(ツ)_");

		return translated;
	}

	private static String replaceWith(String message, String quot, String pre, String suf) {
		String part = message;

		for (String str : getMatches(message, quot + "(.+?)" + quot)) {
			part = part.replaceFirst(quot + Pattern.quote(str) + quot, pre + str + suf);
		}

		return part;
	}

	public static List<String> getMatches(String string, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(string);
		List<String> matches = new ArrayList<>();

		while (matcher.find()) {
			matches.add(matcher.group(1));
		}

		return matches;
	}
}
