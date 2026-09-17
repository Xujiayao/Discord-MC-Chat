package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles a list of regular expressions from configuration, and recompiles them only when the
 * configured list actually changes.
 * <p>
 * Several settings are lists of regexes that are applied on a hot path — every player command, every
 * forwarded console line — so recompiling them per use would be wasteful, while caching them forever
 * would ignore {@code /dmcc reload}. The fingerprint of the configured list is what decides.
 * <p>
 * An instance keeps the last result and its fingerprint. Callers hold the instance in a
 * {@code static final} field, so every thread sees the same cache.
 *
 * @author Xujiayao
 */
public final class CachedPatterns {

	private volatile List<Pattern> compiled = List.of();
	private volatile String fingerprint = null;

	/**
	 * @param configPath Configuration path holding an array of regex strings.
	 * @param onInvalid  Receives one message per regex that fails to compile; the caller decides how to
	 *                   report it, because each list has its own translation key.
	 * @return The compiled patterns for the currently configured list.
	 */
	public List<Pattern> of(String configPath, Consumer<String> onInvalid) {
		List<String> sources = new ArrayList<>();
		JsonNode nodes = ConfigManager.getConfigNode(configPath);
		if (nodes.isArray()) {
			for (JsonNode node : nodes) {
				if (node != null && node.isString() && !node.asString("").isBlank()) {
					sources.add(node.asString(""));
				}
			}
		}

		String current = String.join("\u0000", sources);
		if (current.equals(fingerprint)) {
			return compiled;
		}

		List<Pattern> result = new ArrayList<>();
		for (String source : sources) {
			try {
				result.add(Pattern.compile(source));
			} catch (PatternSyntaxException e) {
				onInvalid.accept(source);
			}
		}

		List<Pattern> published = List.copyOf(result);
		compiled = published;
		fingerprint = current;
		return published;
	}
}
