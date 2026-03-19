package com.xujiayao.discord_mc_chat.server.minecraft;

import java.util.regex.Pattern;

/**
 * Shared regex patterns for Minecraft-origin message parsing.
 *
 * @author Xujiayao
 */
public class MinecraftMessagePatterns {

	public static final Pattern USER_MENTION_PATTERN = Pattern.compile("(?<!\\w)@([A-Za-z0-9_]{3,16})");
	public static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
	public static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("(?<![A-Za-z0-9_]):[A-Za-z0-9_+\\-]+:(?![A-Za-z0-9_])");
	public static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");

	private MinecraftMessagePatterns() {
	}
}
