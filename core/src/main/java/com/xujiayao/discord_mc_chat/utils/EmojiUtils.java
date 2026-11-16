package com.xujiayao.discord_mc_chat.utils;

import net.fellbaum.jemoji.Emoji;
import net.fellbaum.jemoji.EmojiManager;

import java.util.List;

/**
 * Shared helpers for turning a jemoji {@link Emoji} into a stable Discord alias.
 * <p>
 * Jemoji ships fully-qualified emojis (which carry aliases) and unqualified variants (which can carry an
 * empty alias list). Calling {@code getDiscordAliases().getFirst()} on the latter throws, so every alias
 * lookup in DMCC goes through this helper with a hard fallback chain.
 *
 * @author Xujiayao
 */
public final class EmojiUtils {

	private EmojiUtils() {
	}

	/**
	 * Returns the preferred Discord-facing alias of an emoji: the first Discord alias, then the first
	 * GitHub/Slack alias, and finally the raw emoji character. Never throws.
	 */
	public static String preferredDiscordAlias(Emoji emoji) {
		List<String> aliases = emoji.getDiscordAliases();
		if (!aliases.isEmpty()) {
			return aliases.getFirst();
		}

		aliases = emoji.getGithubAliases();
		if (!aliases.isEmpty()) {
			return aliases.getFirst();
		}

		aliases = emoji.getSlackAliases();
		if (!aliases.isEmpty()) {
			return aliases.getFirst();
		}

		return emoji.getEmoji();
	}

	/**
	 * Resolves a Unicode string (e.g. a reaction emoji) to its preferred Discord alias. When jemoji does
	 * not know the exact string, the input is returned unchanged.
	 */
	public static String preferredDiscordAlias(String unicode) {
		return EmojiManager.getEmoji(unicode).map(EmojiUtils::preferredDiscordAlias).orElse(unicode);
	}
}
