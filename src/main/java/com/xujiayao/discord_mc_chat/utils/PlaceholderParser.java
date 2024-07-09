package com.xujiayao.discord_mc_chat.utils;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.node.parent.ClickActionNode;
import eu.pb4.placeholders.api.node.parent.FormattingNode;
import eu.pb4.placeholders.api.node.parent.HoverNode;
import eu.pb4.placeholders.api.parsers.MarkdownLiteParserV1;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.ParserBuilder;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

import static com.xujiayao.discord_mc_chat.Main.SERVER;

/**
 * @author Xujiayao
 */
public class PlaceholderParser {

	private static final NodeParser PARSER = ParserBuilder.of()
			.globalPlaceholders()
			.quickText()
			.simplifiedTextFormat()
			.markdown(MarkdownLiteParserV1::defaultSpoilerFormatting,
					MarkdownLiteParserV1::defaultQuoteFormatting,
					PlaceholderParser::customUrlFormatting,
					MarkdownLiteParserV1.MarkdownFormat.values())
			.build();

	private static TextNode customUrlFormatting(TextNode[] textNodes, TextNode url) {
		// TODO Toggle: Yellow vs Blue
		// TODO Hover "Open URL" translation
		return new ClickActionNode(TextNode.array(new HoverNode<>(TextNode.array(new FormattingNode(textNodes, ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)), HoverNode.Action.TEXT, TextNode.of("Open URL"))), ClickEvent.Action.OPEN_URL, url);
	}

	public static Component parseOtherMessage(String server, Component message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"message", TextNode.convert(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.otherMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseCommandNotice(String name, String roleName, String roleColor, String command) {
		Map<String, TextNode> placeholders = Map.of(
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"roleColor", TextNode.of(roleColor),
				"command", TextNode.of(command)
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.commandNotice")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseResponseMessage(String server, String name, String roleName, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.responseMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseChatMessage(String server, String name, String roleName, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.chatMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}
}
