package com.xujiayao.discord_mc_chat.server.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for splitting long messages into Discord-safe code blocks.
 *
 * @author Xujiayao
 */
public final class CodeBlockMessageUtils {

	private static final int DISCORD_MESSAGE_LIMIT = 2000;
	private static final int CODE_BLOCK_WRAPPER_LENGTH = 8; // "```\n" + "\n```"
	private static final int CODE_BLOCK_CONTENT_LIMIT = DISCORD_MESSAGE_LIMIT - CODE_BLOCK_WRAPPER_LENGTH;

	private CodeBlockMessageUtils() {
	}

	/**
	 * Splits a message into multiple Discord code block payloads under the 2000-character limit.
	 * <p>
	 * Input text is normalized to LF line endings and backticks are replaced to avoid breaking
	 * markdown fences.
	 *
	 * @param message Source message text.
	 * @return One or more code block strings safe to send to Discord.
	 */
	public static List<String> splitToCodeBlocks(String message) {
		String normalized = normalize(message);
		if (normalized.isEmpty()) {
			return List.of("```\n\n```");
		}

		List<String> blocks = new ArrayList<>();
		int index = 0;
		while (index < normalized.length()) {
			int end = Math.min(index + CODE_BLOCK_CONTENT_LIMIT, normalized.length());
			if (end < normalized.length()) {
				int lastNewline = normalized.lastIndexOf('\n', end - 1);
				if (lastNewline >= index) {
					end = lastNewline + 1;
				}
			}

			// A high surrogate as the last kept char would be separated from its low surrogate and
			// render as a broken character, so back off by one (same idea as safeTruncate).
			if (end < normalized.length() && end > index && Character.isHighSurrogate(normalized.charAt(end - 1))) {
				end--;
			}

			blocks.add("```\n" + normalized.substring(index, end) + "\n```");
			index = end;
		}

		return blocks;
	}

	private static String normalize(String message) {
		if (message == null) {
			return "";
		}
		return message
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replace("```", "'''");
	}
}
