/*
This file was obtained from BRForgers/DisFabric on December 31, 2020, licensed under the Mozilla Public License 2.0 (MPL-2.0).

Author: allanf181 (Allan Fernando)

Link to the original source:
https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/src/main/java/br/com/brforgers/mods/disfabric/utils/MarkdownParser.java

Link to the license:
https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/LICENSE

Note that the "Don't Be a Jerk" license used in the newly created project BRForgers/DisFabric-and-DisForge is separate from the old project BRForgers/DisFabric.

- Any recent changes in the project name and license do not retroactively affect the license terms of the code obtained at a specific moment before, that is, December 31, 2020.
- Any files obtained from BRForgers/DisFabric continue to be subject to the terms of the MPL-2.0 license only.

Link to the "Don't Be a Jerk" license:
https://github.com/BRForgers/DisFabric-and-DisForge/blob/d1468a6c9b50ba24a250ec370cf645d58dccdfd1/LICENSE.md
 */

package com.xujiayao.discord_mc_chat.utils;

import net.minecraft.ChatFormatting;

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
		message = replaceWith(message, "(?<!\\\\)\\*\\*", ChatFormatting.BOLD.toString(), ChatFormatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)\\*", ChatFormatting.ITALIC.toString(), ChatFormatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)__", ChatFormatting.UNDERLINE.toString(), ChatFormatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)_", ChatFormatting.ITALIC.toString(), ChatFormatting.RESET.toString());
		message = replaceWith(message, "(?<!\\\\)~~", ChatFormatting.STRIKETHROUGH.toString(), ChatFormatting.RESET.toString());

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
