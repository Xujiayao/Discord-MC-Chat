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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.fellbaum.jemoji.EmojiManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
//#if MC < 11900
//$$ import net.minecraft.network.chat.TextComponent;
//#endif

import java.util.Map;
import java.util.Objects;

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
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
		// TODO Hover "Open URL"
		return new ClickActionNode(TextNode.array(new FormattingNode(textNodes, ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)), ClickEvent.Action.OPEN_URL, url);
	}

	public static Component parseOtherMessage(Component message) {
		Map<String, Component> placeholders = Map.of(
				//#if MC >= 11900
				"server", Component.literal(CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"),
				"message", message
				//#else
				//$$ "server", new TextComponent(CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"),
				//$$ "message", message
				//#endif
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholderText(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.otherMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseCommandNotice(SlashCommandInteractionEvent e) {
		Objects.requireNonNull(e.getMember());

		Map<String, Component> placeholders = Map.of(
				//#if MC >= 11900
				"name", Component.literal(CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()),
				"roleName", Component.literal(e.getMember().getRoles().stream().map(Role::getName).findFirst().orElse("null")),
				"roleColor", Component.literal(String.format("#%06X", (0xFFFFFF & e.getMember().getColorRaw()))),
				"command", Component.literal(e.getCommandString())
				//#else
				//$$ "name", new TextComponent(CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()),
				//$$ "roleName", new TextComponent(e.getMember().getRoles().stream().map(Role::getName).findFirst().orElse("null")),
				//$$ "roleColor", new TextComponent(String.format("#%06X", (0xFFFFFF & e.getMember().getColorRaw()))),
				//$$ "command", new TextComponent(e.getCommandString())
				//#endif
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholderText(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.commandNotice")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseResponseMessage(Member referencedMember, String webhookName, String referencedMemberRoleName, String referencedMessageTemp) {
		Map<String, Component> placeholders = Map.of(
				//#if MC >= 11900
				"server", Component.literal("Discord"),
				"name", Component.literal((referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()) : webhookName),
				"roleName", Component.literal(referencedMemberRoleName),
				"message", Component.literal(EmojiManager.replaceAllEmojis(referencedMessageTemp, emoji -> emoji.getDiscordAliases().getFirst()))
				//#else
				//$$ "server", new TextComponent("Discord"),
				//$$ "name", new TextComponent((referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()) : webhookName),
				//$$ "roleName", new TextComponent(referencedMemberRoleName),
				//$$ "message", new TextComponent(EmojiManager.replaceAllEmojis(referencedMessageTemp, emoji -> emoji.getDiscordAliases().getFirst()))
				//#endif
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholderText(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.responseMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseChatMessage(MessageReceivedEvent e, String memberRoleName, String messageTemp) {
		Objects.requireNonNull(e.getMember());

		Map<String, Component> placeholders = Map.of(
				//#if MC >= 11900
				"server", Component.literal("Discord"),
				"name", Component.literal(CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()),
				"roleName", Component.literal(memberRoleName),
				"message", Component.literal(EmojiManager.replaceAllEmojis(messageTemp, emoji -> emoji.getDiscordAliases().getFirst()))
				//#else
				//$$ "server", new TextComponent("Discord"),
				//$$ "name", new TextComponent(CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()),
				//$$ "roleName", new TextComponent(memberRoleName),
				//$$ "message", new TextComponent(EmojiManager.replaceAllEmojis(messageTemp, emoji -> emoji.getDiscordAliases().getFirst()))
				//#endif
		);

		NodeParser parser = ParserBuilder.of()
				.add(PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholderText(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.chatMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}
}
