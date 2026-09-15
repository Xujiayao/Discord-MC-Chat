package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Renders the {@code custom_messages} templates that wrap chat content.
 * <p>
 * Every template is a YAML list of {@code {text, bold, color}} parts. A part that contains
 * {@code {message}} receives the parsed message body, and any other part is emitted verbatim. This single
 * renderer replaces the seven near-identical {@code buildXxxSegments} methods that used to live in the
 * Discord and Minecraft parsers.
 *
 * @author Xujiayao
 */
final class MessageTemplates {

	private static final String MESSAGE = "{message}";

	private MessageTemplates() {
	}

	/**
	 * Starts rendering the given template node.
	 *
	 * @param template Template node from {@code custom_messages}. May be missing, in which case nothing is
	 *                 rendered.
	 * @return A builder collecting placeholders and content.
	 */
	static Builder of(JsonNode template) {
		return new Builder(template);
	}

	/**
	 * Fluent template renderer.
	 */
	static final class Builder {

		private final JsonNode template;
		private final Map<String, String> placeholders = new LinkedHashMap<>();
		private Supplier<List<TextSegment>> content;
		private boolean yamlMultilineHeader;

		private Builder(JsonNode template) {
			this.template = template;
		}

		/**
		 * Registers a placeholder such as {@code server} for the token {@code {server}}. Null values are
		 * ignored so that callers can pass optional data straight through.
		 */
		Builder with(String name, String value) {
			if (value != null) {
				placeholders.put("{" + name + "}", value);
			}
			return this;
		}

		/**
		 * Feeds the parsed message body into every part that contains {@code {message}}.
		 */
		Builder content(Supplier<List<TextSegment>> content) {
			this.content = content;
			return this;
		}

		/**
		 * Emits the multi-line marker used by the chat template: {@code [server] <name> |} followed by the
		 * content on the following lines.
		 */
		Builder yamlMultilineHeader() {
			this.yamlMultilineHeader = true;
			return this;
		}

		/**
		 * @return The rendered segments, or an empty list when the template node is not an array.
		 */
		List<TextSegment> render() {
			List<TextSegment> segments = new ArrayList<>();
			if (!template.isArray()) {
				return segments;
			}
			for (JsonNode part : template) {
				boolean bold = part.path("bold").asBoolean(false);
				String color = substitute(part.path("color").asString(""));
				String text = substitute(part.path("text").asString(""));

				if (content == null || !text.contains(MESSAGE)) {
					segments.add(new TextSegment(text, bold, color));
					continue;
				}

				String[] parts = text.split("\\{message}", -1);
				for (int i = 0; i < parts.length; i++) {
					if (i > 0) {
						if (yamlMultilineHeader) {
							segments.add(new TextSegment("|", bold, color));
						}
						List<TextSegment> body = content.get();
						TextSegment.applyDefaultColor(body, color);
						if (yamlMultilineHeader && !body.isEmpty()) {
							body.getFirst().text = "\n" + body.getFirst().text;
						}
						segments.addAll(body);
					}
					if (!parts[i].isEmpty()) {
						segments.add(new TextSegment(parts[i], bold, color));
					}
				}
			}
			return segments;
		}

		/**
		 * Replaces {@code {name}} tokens in a single pass.
		 * <p>
		 * A single pass matters: replacing sequentially would let a shorter key swallow a longer one
		 * ({@code {server}} is a prefix of {@code {server_color}}) and would re-scan text that came from a
		 * placeholder value.
		 */
		private String substitute(String text) {
			if (text == null || text.isEmpty() || text.indexOf('{') < 0) {
				return text;
			}
			StringBuilder out = new StringBuilder(text.length() + 16);
			int i = 0;
			while (i < text.length()) {
				int open = text.indexOf('{', i);
				if (open < 0) {
					out.append(text, i, text.length());
					break;
				}
				int close = text.indexOf('}', open + 1);
				if (close < 0) {
					out.append(text, i, text.length());
					break;
				}
				out.append(text, i, open);
				String value = placeholders.get(text.substring(open, close + 1));
				if (value == null) {
					out.append('{');
					i = open + 1;
				} else {
					out.append(value);
					i = close + 1;
				}
			}
			return out.toString();
		}
	}
}
