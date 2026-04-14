package com.xujiayao.discord_mc_chat.server.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for splitting long messages into Discord-safe code blocks.
 */
public final class CodeBlockMessageUtils {

	private static final int DISCORD_MESSAGE_LIMIT = 2000;
	private static final int CODE_BLOCK_WRAPPER_LENGTH = 8; // "```\n" + "\n```"
	private static final int CODE_BLOCK_CONTENT_LIMIT = DISCORD_MESSAGE_LIMIT - CODE_BLOCK_WRAPPER_LENGTH;

	private CodeBlockMessageUtils() {
	}

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

			if (end <= index) {
				end = Math.min(index + CODE_BLOCK_CONTENT_LIMIT, normalized.length());
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

