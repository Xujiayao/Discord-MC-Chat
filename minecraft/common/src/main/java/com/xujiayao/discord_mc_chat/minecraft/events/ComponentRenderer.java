package com.xujiayao.discord_mc_chat.minecraft.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.util.List;

/**
 * Renders DMCC's {@link TextSegment} lists as Minecraft components, and converts them to and from the
 * JSON form used to relay tellraw output between clients.
 * <p>
 * Deliberately stateless apart from the registry ops captured at server start: it needs no server
 * reference, so it can be exercised without a running game.
 *
 * @author Xujiayao
 */
public final class ComponentRenderer {

	/**
	 * Built once at server start, because creating it per call would re-resolve the whole registry.
	 */
	private static volatile RegistryOps<JsonElement> registryOps;

	private ComponentRenderer() {
	}

	/**
	 * Captures the registry ops of the running server.
	 */
	static void init(MinecraftServer server) {
		registryOps = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
	}

	/**
	 * @param segments Message segments, possibly null or empty.
	 * @return The rendered component, empty when there is nothing to render.
	 */
	public static Component toComponent(List<TextSegment> segments) {
		if (segments == null || segments.isEmpty()) {
			return Component.empty();
		}

		MutableComponent root = Component.empty();
		for (TextSegment segment : segments) {
			root.append(toPart(segment));
		}
		return root;
	}

	/**
	 * Renders segments while substituting a native component for a placeholder token.
	 *
	 * @param placeholder Token to look for, e.g. the {@code __DMCC_TELLRAW_COMPONENT__} marker.
	 * @param replacement Component to swap in, e.g. the rebuilt tellraw payload.
	 * @return The rendered component, or the replacement itself when there is nothing to render.
	 */
	public static Component toComponentReplacingPlaceholder(List<TextSegment> segments, String placeholder,
															Component replacement) {
		if (segments == null || segments.isEmpty()) {
			return replacement;
		}

		MutableComponent root = Component.empty();
		for (TextSegment segment : segments) {
			if (segment.text == null || segment.text.isEmpty() || !segment.text.contains(placeholder)) {
				root.append(toPart(segment));
				continue;
			}

			int cursor = 0;
			while (cursor < segment.text.length()) {
				int placeholderStart = segment.text.indexOf(placeholder, cursor);
				if (placeholderStart < 0) {
					String tail = segment.text.substring(cursor);
					if (!tail.isEmpty()) {
						root.append(toPart(TextSegment.copyOf(segment, tail)));
					}
					break;
				}

				if (placeholderStart > cursor) {
					String leading = segment.text.substring(cursor, placeholderStart);
					root.append(toPart(TextSegment.copyOf(segment, leading)));
				}

				root.append(replacement.copy());
				cursor = placeholderStart + placeholder.length();
			}
		}

		return root;
	}

	public static MutableComponent toPart(TextSegment segment) {
		MutableComponent part = Component.literal(segment.text == null ? "" : segment.text);
		Style style = Style.EMPTY;

		if (segment.color != null && !segment.color.isEmpty()) {
			TextColor textColor = TextColor.parseColor(segment.color).result().orElse(null);
			if (textColor != null) {
				style = style.withColor(textColor);
			}
		}

		if (segment.bold) {
			style = style.withBold(true);
		}
		if (segment.italic) {
			style = style.withItalic(true);
		}
		if (segment.underlined) {
			style = style.withUnderlined(true);
		}
		if (segment.strikethrough) {
			style = style.withStrikethrough(true);
		}
		if (segment.obfuscated) {
			style = style.withObfuscated(true);
		}

		if (segment.clickUrl != null && !segment.clickUrl.isEmpty()) {
			try {
				style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(segment.clickUrl)));
			} catch (Exception ignored) {
				// Invalid URL, skip click event
			}
		}

		if (segment.hoverText != null && !segment.hoverText.isEmpty()) {
			style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(segment.hoverText)));
		}

		part.withStyle(style);
		return part;
	}

	/**
	 * @param component Component to serialize.
	 * @return The JSON form, or an empty string when it cannot be produced.
	 */
	public static String toJson(Component component) {
		if (component == null) {
			return "";
		}
		RegistryOps<JsonElement> ops = registryOps;
		if (ops == null) {
			return "";
		}
		try {
			return ComponentSerialization.CODEC
					.encodeStart(ops, component)
					.result()
					.map(Object::toString)
					.orElse("");
		} catch (Exception ignored) {
			return "";
		}
	}

	/**
	 * @param json A component in its JSON form.
	 * @return The parsed component, or null when the input is empty or malformed.
	 */
	public static Component fromJson(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		RegistryOps<JsonElement> ops = registryOps;
		if (ops == null) {
			return null;
		}
		try {
			return ComponentSerialization.CODEC
					.parse(ops, JsonParser.parseString(json))
					.result()
					.orElse(null);
		} catch (Exception ignored) {
			return null;
		}
	}

}
