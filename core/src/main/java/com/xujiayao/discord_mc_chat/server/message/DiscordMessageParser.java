package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.MessageParserCommon.MarkdownState;
import com.xujiayao.discord_mc_chat.utils.TextSegmentUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.fellbaum.jemoji.EmojiManager;
import tools.jackson.databind.JsonNode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Discord messages into pre-built {@link TextSegment} lists for Minecraft rendering.
 * <p>
 * All parsing decisions are driven by the {@code message_parsing.discord_to_minecraft.*} config
 * switches. The server builds the full rich-text representation so that DMCC clients can
 * directly convert the segments into Minecraft Components without accessing Discord APIs
 * or custom_messages.
 *
 * @author Xujiayao
 */
public final class DiscordMessageParser {

	// Discord Markdown patterns
	private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n?([\\s\\S]*?)```");

	// Discord mention patterns in raw content
	private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");

	// @everyone / @here pattern in raw content
	private static final Pattern EVERYONE_HERE_PATTERN = Pattern.compile("@(everyone|here)");

	// Discord timestamp pattern: <t:EPOCH> or <t:EPOCH:STYLE>
	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");

	// Discord custom emoji patterns
	private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:(\\w+):\\d+>");
	private static final Pattern DISCORD_ALIAS_EMOJI_PATTERN = Pattern.compile("(?<![A-Za-z0-9_]):[A-Za-z0-9_+\\-]+:(?![A-Za-z0-9_])");

	// ANSI escape sequence pattern for ```ansi code blocks
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\x1B\\[(\\d+(?:;\\d+)*)m");

	// Matches spoiler-wrapped user mentions: ||<@123>|| / ||<@!123>||
	private static final Pattern SPOILER_USER_MENTION_PATTERN = Pattern.compile("\\|\\|<@!?(\\d+)>\\|\\|");
	// Matches spoiler-wrapped role mentions: ||<@&123>||
	private static final Pattern SPOILER_ROLE_MENTION_PATTERN = Pattern.compile("\\|\\|<@&(\\d+)>\\|\\|");
	// Matches spoiler-wrapped channel mentions: ||<#123>||
	private static final Pattern SPOILER_CHANNEL_MENTION_PATTERN = Pattern.compile("\\|\\|<#(\\d+)>\\|\\|");
	// Matches spoiler-wrapped @everyone/@here tokens: ||@everyone|| / ||@here||
	private static final Pattern SPOILER_EVERYONE_HERE_PATTERN = Pattern.compile("\\|\\|@(everyone|here)\\|\\|");
	private static final Pattern SPOILER_CONTENT_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
	// Markdown/whitespace characters removed when comparing a spoiler body with an embed URL
	private static final Pattern SPOILER_URL_STRIP_PATTERN = Pattern.compile("[*_~`\\s]");
	private static final List<String> MARKDOWN_DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");

	// Matches the {message} placeholder inside custom_messages templates
	private static final Pattern MESSAGE_PLACEHOLDER_PATTERN = Pattern.compile("\\{message}");

	private static final int MAX_CONTENT_LINES = 6;
	private static final int REPLY_TRUNCATE_LIMIT_WIDE = 20;
	private static final int REPLY_TRUNCATE_LIMIT_NARROW = 40;
	private static final int MAIN_TRUNCATE_LIMIT_WIDE = 200;
	private static final int MAIN_TRUNCATE_LIMIT_NARROW = 400;
	private static final String URL_COLOR = "#3366CC";
	private static final String QUOTE_COLOR = "gray";
	private static final String ATTACHMENT_LABEL_PREFIX = "<attachment type=[%s] name=[";
	private static final String EMBED_LABEL_PREFIX = "<embed title=[";
	private static final String LABEL_SUFFIX = "]>";

	private DiscordMessageParser() {
	}

	/**
	 * Custom messages are not loaded in {@code multi_server_client} mode, and stay unset when the
	 * custom_messages file fails to load, so every template lookup must tolerate a null root.
	 *
	 * @return The template node at the given path, or null when custom messages are unavailable.
	 */
	private static JsonNode customMessageNode(String... path) {
		JsonNode node = I18nManager.getCustomMessages();
		if (node == null) {
			return null;
		}
		for (String part : path) {
			node = node.path(part);
		}
		return node;
	}

	public static List<TextSegment> buildChatSegments(Message message) {
		Member member = message.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = getRoleColorHex(member);

		String truncatedRaw = truncateMainRaw(message.getContentRaw());
		boolean isMultiLine = truncatedRaw.contains("\n");

		return buildTemplateSegments(
				customMessageNode("xxxxx_to_minecraft", "user_message"),
				text -> replacePlaceholders(text, effectiveName, roleColor),
				color -> replacePlaceholders(color, effectiveName, roleColor),
				(out, color, bold) -> {
					if (isMultiLine) {
						// YAML-style multi-line: a "|" marker followed by the newline-separated content
						out.add(new TextSegment("|", bold, color));
					}
					List<TextSegment> contentSegments = parseMessageContent(message, truncatedRaw);
					TextSegmentUtils.applyDefaultColor(contentSegments, color);
					if (isMultiLine && !contentSegments.isEmpty()) {
						contentSegments.getFirst().text = "\n" + contentSegments.getFirst().text;
					}
					out.addAll(contentSegments);
				}
		);
	}

	public static List<TextSegment> buildCommandSegments(String effectiveName, String roleColor, String commandName) {
		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "command"),
				text -> text.replace("{effective_name}", effectiveName)
						.replace("{role_color}", roleColor)
						.replace("{command}", commandName),
				color -> color.replace("{role_color}", roleColor),
				null
		);
	}

	public static List<TextSegment> buildReplySegments(Message referencedMessage) {
		if (referencedMessage == null) {
			return null;
		}
		Member refMember = referencedMessage.getMember();
		String refName = refMember != null ? refMember.getEffectiveName() : referencedMessage.getAuthor().getName();
		String refRoleColor = getRoleColorHex(refMember);
		return buildReplySegments(refName, refRoleColor, referencedMessage, referencedMessage.getContentRaw());
	}

	public static List<TextSegment> buildReplySegments(String refName, String refRoleColor, Message contextMessage, String refRaw) {
		if (refRaw == null) {
			return null;
		}
		String truncatedRaw = truncateReplyRaw(refRaw);
		List<TextSegment> refContentSegments = enforceSingleLine(contextMessage != null
				? parseMessageContent(contextMessage, truncatedRaw)
				: parseMessageContentWithoutMessage(truncatedRaw));

		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "response"),
				text -> text.replace("{effective_name}", refName),
				color -> color.replace("{role_color}", refRoleColor),
				(out, color, bold) -> {
					TextSegmentUtils.applyDefaultColor(refContentSegments, color);
					out.addAll(refContentSegments);
				}
		);
	}

	public static List<TextSegment> buildReactionSegments(String reactorName, String roleColor, String emojiText) {
		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "reaction"),
				text -> text.replace("{effective_name}", reactorName).replace("{emoji}", emojiText),
				color -> color.replace("{role_color}", roleColor),
				null
		);
	}

	public static List<TextSegment> buildEditNotificationSegments(String editorName, String roleColor) {
		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "edit"),
				text -> text.replace("{effective_name}", editorName),
				color -> color.replace("{role_color}", roleColor),
				null
		);
	}

	public static List<TextSegment> buildEditedMessageSegments(Message message) {
		Member member = message.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = getRoleColorHex(member);
		List<TextSegment> contentSegments = parseMessageContent(message, truncateMainRaw(message.getContentRaw()));

		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "edited_message"),
				text -> text.replace("{effective_name}", effectiveName),
				color -> color.replace("{role_color}", roleColor),
				(out, color, bold) -> {
					TextSegmentUtils.applyDefaultColor(contentSegments, color);
					out.addAll(contentSegments);
				}
		);
	}

	public static List<TextSegment> buildDeleteSegments(String deleterName, String roleColor) {
		return buildTemplateSegments(
				customMessageNode("discord_to_minecraft", "delete"),
				text -> text.replace("{effective_name}", deleterName),
				color -> color.replace("{role_color}", roleColor),
				null
		);
	}

	@FunctionalInterface
	private interface MessageContentInserter {
		void insert(List<TextSegment> segments, String color, boolean bold);
	}

	/**
	 * Builds the rendered segments of a custom_messages template array. Placeholders are replaced by
	 * the given operators; a segment containing {@code {message}} is split around the placeholder and
	 * its content is inserted by {@code contentInserter} (null when the template has no message body).
	 */
	private static List<TextSegment> buildTemplateSegments(JsonNode templateNode,
	                                                       UnaryOperator<String> textReplacer,
	                                                       UnaryOperator<String> colorReplacer,
	                                                       MessageContentInserter contentInserter) {
		List<TextSegment> segments = new ArrayList<>();
		if (templateNode == null || !templateNode.isArray()) {
			return segments;
		}
		for (JsonNode segNode : templateNode) {
			String text = textReplacer.apply(segNode.path("text").asString(""));
			boolean bold = segNode.path("bold").asBoolean(false);
			String color = colorReplacer.apply(segNode.path("color").asString(""));

			if (contentInserter == null || !text.contains("{message}")) {
				segments.add(new TextSegment(text, bold, color));
				continue;
			}

			String[] parts = MESSAGE_PLACEHOLDER_PATTERN.split(text, -1);
			if (!parts[0].isEmpty()) {
				segments.add(new TextSegment(parts[0], bold, color));
			}
			contentInserter.insert(segments, color, bold);
			if (parts.length > 1 && !parts[1].isEmpty()) {
				segments.add(new TextSegment(parts[1], bold, color));
			}
		}
		return segments;
	}

	public static String getMentionNotificationText(String effectiveName) {
		JsonNode mentionedNode = customMessageNode("xxxxx_to_minecraft", "mentioned");
		String template = mentionedNode == null ? "" : mentionedNode.asString();
		return template.replace("{effective_name}", effectiveName);
	}

	public static boolean isMentionEveryone(Message message) {
		return message.getMentions().mentionsEveryone();
	}

	public static Set<String> collectMentionedPlayerUuids(Message message) {
		Set<String> uuids = new HashSet<>();

		// Direct user mentions
		for (User mentionedUser : message.getMentions().getUsers()) {
			List<String> linkedUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(mentionedUser.getId());
			uuids.addAll(linkedUuids);
		}

		// Role mentions
		for (Role mentionedRole : message.getMentions().getRoles()) {
			List<Member> membersWithRole = message.getGuild().getMembersWithRoles(mentionedRole);
			for (Member m : membersWithRole) {
				List<String> linkedUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(m.getUser().getId());
				uuids.addAll(linkedUuids);
			}
		}

		// @everyone / @here mentions are handled via the mentionEveryone flag
		// which notifies ALL online players, not just linked ones

		return uuids;
	}

	public static List<TextSegment> parseMessageContent(Message message, String raw) {
		List<TextSegment> segments = new ArrayList<>();

		boolean parseMentions = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.mentions");
		boolean parseCustomEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.custom_emojis");
		boolean parseUnicodeEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.unicode_emojis");
		boolean parseMarkdown = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown");
		boolean parseHyperlinks = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.hyperlinks");
		boolean parseAttachments = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.attachments");
		boolean parseStickers = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.stickers");
		boolean parseEmbeds = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.embeds");
		boolean parseComponents = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.components");
		boolean parseTimestamps = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.timestamps");

		// Process the raw text content
		if (!raw.isEmpty()) {
			segments.addAll(parseRawContent(raw, message, parseMentions, parseCustomEmojis,
					parseUnicodeEmojis, parseMarkdown, parseHyperlinks, parseTimestamps));
		}

		// Append attachments
		if (parseAttachments) {
			for (Message.Attachment attachment : message.getAttachments()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String type = "file";
				if (attachment.isImage()) {
					type = "image";
				} else if (attachment.isVideo()) {
					type = "video";
				}
				boolean spoilerAttachment = attachment.isSpoiler() || attachment.getFileName().startsWith("SPOILER_");
				segments.addAll(buildAttachmentSegments(type, attachment.getFileName(), attachment.getUrl(), spoilerAttachment));
			}
		}

		// Append stickers
		if (parseStickers) {
			for (StickerItem sticker : message.getStickers()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				segments.add(new TextSegment("<sticker name=[" + sticker.getName() + "]>", false, "yellow"));
			}
		}

		// Append embeds
		if (parseEmbeds) {
			for (MessageEmbed embed : message.getEmbeds()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String title = embed.getTitle() != null ? embed.getTitle() : "";
				if (title.isEmpty() && embed.getDescription() != null) {
					title = embed.getDescription();
					if (title.length() > 50) {
						title = safeTruncate(title, 50) + "...";
					}
				}

				boolean spoilerEmbed = isSpoilerWrappedUrl(raw, embed.getUrl());
				segments.addAll(buildEmbedSegments(title, embed.getUrl(), spoilerEmbed));
			}
		}

		// Append interactive components indicator
		if (parseComponents && !message.getComponents().isEmpty()) {
			if (!segments.isEmpty()) {
				segments.add(new TextSegment(" "));
			}
			segments.add(new TextSegment("<components>", false, "yellow"));
		}

		// Append poll indicator
		if (ConfigManager.getBoolean("message_parsing.discord_to_minecraft.polls")) {
			MessagePoll poll = message.getPoll();
			if (poll != null) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String question = poll.getQuestion().getText();
				segments.add(new TextSegment("<poll question=[" + question + "]>", false, "yellow"));
			}
		}

		return segments;
	}

	private static List<TextSegment> parseRawContent(String raw, Message message,
	                                                 boolean parseMentions, boolean parseCustomEmojis,
	                                                 boolean parseUnicodeEmojis, boolean parseMarkdown,
	                                                 boolean parseHyperlinks, boolean parseTimestamps) {
		List<TextSegment> segments = new ArrayList<>();

		// Mentions/timestamps are split after Markdown so nested formatting (e.g. **<@id>**) is preserved.
		if (parseMarkdown) {
			segments.addAll(parseMarkdownText(raw));
			return postProcessInlineSegments(segments, message, parseMentions, parseTimestamps,
					parseCustomEmojis, parseUnicodeEmojis, parseHyperlinks);
		}

		List<TokenSpan> tokens = new ArrayList<>();

		if (parseMentions) {
			collectUserMentionTokens(raw, message, tokens, SPOILER_USER_MENTION_PATTERN, true);
			collectRoleMentionTokens(raw, message, tokens, SPOILER_ROLE_MENTION_PATTERN, true);
			collectChannelMentionTokens(raw, message, tokens, SPOILER_CHANNEL_MENTION_PATTERN, true);
			collectEveryoneHereTokens(raw, message, tokens, SPOILER_EVERYONE_HERE_PATTERN, true);
			collectUserMentionTokens(raw, message, tokens, USER_MENTION_PATTERN, false);
			collectRoleMentionTokens(raw, message, tokens, ROLE_MENTION_PATTERN, false);
			collectChannelMentionTokens(raw, message, tokens, CHANNEL_MENTION_PATTERN, false);
			collectEveryoneHereTokens(raw, message, tokens, EVERYONE_HERE_PATTERN, false);
		}

		if (parseTimestamps) {
			collectTimestampTokens(raw, tokens);
		}

		// Hyperlinks and emoji are parsed after Markdown so nested wrappers don't leak as plain text.

		// Sort tokens by start position
		tokens.sort(Comparator.comparingInt(a -> a.start));

		// Remove overlapping tokens (keep the first one)
		tokens = removeOverlaps(tokens);

		// Build segments from the raw text, inserting special tokens
		int cursor = 0;
		for (TokenSpan token : tokens) {
			if (token.start > cursor) {
				String plainText = raw.substring(cursor, token.start);
				segments.add(new TextSegment(plainText));
			}
			segments.add(token.segment);
			cursor = token.end;
		}

		// Remaining text after last token
		if (cursor < raw.length()) {
			String remaining = raw.substring(cursor);
			segments.add(new TextSegment(remaining));
		}

		return postProcessInlineSegments(segments, message, false, false,
				parseCustomEmojis, parseUnicodeEmojis, parseHyperlinks);
	}

	private static List<TextSegment> parseMessageContentWithoutMessage(String raw) {
		boolean parseCustomEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.custom_emojis");
		boolean parseUnicodeEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.unicode_emojis");
		boolean parseMarkdown = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown");
		boolean parseHyperlinks = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.hyperlinks");
		boolean parseTimestamps = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.timestamps");
		return parseRawContent(raw, null, false, parseCustomEmojis, parseUnicodeEmojis, parseMarkdown, parseHyperlinks, parseTimestamps);
	}

	private static void collectUserMentionTokens(String raw, Message message, List<TokenSpan> tokens, Pattern pattern, boolean spoiler) {
		Matcher matcher = pattern.matcher(raw);
		while (matcher.find()) {
			String userId = matcher.group(1);
			String displayName = null;
			String color = null;
			for (User user : message.getMentions().getUsers()) {
				if (user.getId().equals(userId)) {
					Member member = message.getGuild().getMember(user);
					displayName = member != null ? member.getEffectiveName() : user.getName();
					color = getRoleColorHex(member);
					break;
				}
			}
			if (displayName == null) {
				displayName = userId;
			}
			TextSegment seg = new TextSegment("[@" + displayName + "]", false, colorOrDefault(color));
			if (spoiler) {
				applySpoilerStyle(seg);
			}
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectRoleMentionTokens(String raw, Message message, List<TokenSpan> tokens, Pattern pattern, boolean spoiler) {
		Matcher matcher = pattern.matcher(raw);
		while (matcher.find()) {
			String roleId = matcher.group(1);
			String roleName = roleId;
			String color = "white";
			for (Role role : message.getMentions().getRoles()) {
				if (role.getId().equals(roleId)) {
					roleName = role.getName();
					Color roleColor = role.getColors().getPrimary();
					if (roleColor != null) {
						color = "#%06X".formatted(roleColor.getRGB() & 0xFFFFFF);
					}
					break;
				}
			}
			TextSegment seg = new TextSegment("[@" + roleName + "]", false, color);
			if (spoiler) {
				applySpoilerStyle(seg);
			}
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectChannelMentionTokens(String raw, Message message, List<TokenSpan> tokens, Pattern pattern, boolean spoiler) {
		Matcher matcher = pattern.matcher(raw);
		while (matcher.find()) {
			String channelId = matcher.group(1);
			String channelName = channelId;
			for (GuildChannel channel : message.getMentions().getChannels()) {
				if (channel.getId().equals(channelId)) {
					channelName = channel.getName();
					break;
				}
			}
			TextSegment seg = new TextSegment("[#" + channelName + "]", false, "yellow");
			if (spoiler) {
				applySpoilerStyle(seg);
			}
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectEveryoneHereTokens(String raw, Message message, List<TokenSpan> tokens, Pattern pattern, boolean spoiler) {
		if (!message.getMentions().mentionsEveryone()) {
			return;
		}
		Matcher matcher = pattern.matcher(raw);
		while (matcher.find()) {
			TextSegment seg = new TextSegment("[@" + matcher.group(1) + "]", false, "yellow");
			if (spoiler) {
				applySpoilerStyle(seg);
			}
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectTimestampTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(raw);
		while (matcher.find()) {
			try {
				long epoch = Long.parseLong(matcher.group(1));
				String style = matcher.group(2);
				String formatted = MessageParserCommon.formatDiscordTimestamp(epoch, style);
				TextSegment seg = new TextSegment("[" + formatted + "]", false, "yellow");
				tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
			} catch (Exception ignored) {
				// If parsing fails, leave the raw token as-is
			}
		}
	}

	public static String formatDiscordTimestampsForPlainText(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String replacement = matcher.group();
			try {
				long epoch = Long.parseLong(matcher.group(1));
				String style = matcher.group(2);
				replacement = "[" + MessageParserCommon.formatDiscordTimestamp(epoch, style) + "]";
			} catch (Exception ignored) {
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private interface Span {
		int start();

		int end();
	}

	private static <T extends Span> List<T> removeOverlaps(List<T> spans) {
		List<T> result = new ArrayList<>();
		int lastEnd = -1;
		for (T span : spans) {
			if (span.start() >= lastEnd) {
				result.add(span);
				lastEnd = span.end();
			}
		}
		return result;
	}

	private static List<TextSegment> parseMarkdownText(String text) {
		List<TextSegment> segments = new ArrayList<>();

		List<MarkdownSpan> spans = new ArrayList<>();
		collectCodeBlockSpans(text, spans);
		spans.sort(Comparator.comparingInt(Span::start));
		spans = removeOverlaps(spans);

		int cursor = 0;
		for (MarkdownSpan span : spans) {
			if (span.start > cursor) {
				segments.addAll(parseMarkdownInlineWithHeading(text.substring(cursor, span.start), new MarkdownState()));
			}
			segments.addAll(span.codeBlockSegments);
			cursor = span.end;
		}

		if (cursor < text.length()) {
			segments.addAll(parseMarkdownInlineWithHeading(text.substring(cursor), new MarkdownState()));
		}

		return segments;
	}

	private static List<TextSegment> parseMarkdownInlineWithHeading(String text, MarkdownState baseState) {
		List<TextSegment> result = new ArrayList<>();
		if (text.isEmpty()) {
			return result;
		}
		int start = 0;
		while (start < text.length()) {
			int newline = text.indexOf('\n', start);
			int lineEnd = newline >= 0 ? newline : text.length();
			String line = text.substring(start, lineEnd);
			MarkdownState lineState = baseState.copy();
			if (MessageParserCommon.isMarkdownQuoteLine(line)) {
				lineState.color = QUOTE_COLOR;
			}
			if (MessageParserCommon.isMarkdownHeadingLine(line)) {
				lineState.bold = true;
			}
			result.addAll(parseNestedMarkdown(line, lineState));
			if (newline < 0) {
				break;
			}
			result.add(new TextSegment("\n"));
			start = newline + 1;
		}
		return result;
	}

	private static List<TextSegment> parseNestedMarkdown(String text, MarkdownState state) {
		List<TextSegment> segments = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			if (text.charAt(i) == '\\' && i + 1 < text.length()) {
				plain.append(text.charAt(i + 1));
				i += 2;
				continue;
			}
			if (text.charAt(i) == '`') {
				int close = findClosingDelimiter(text, i + 1, "`");
				if (close > i) {
					appendPlainSegment(segments, plain, state);
					addStyledSegment(segments, "[" + text.substring(i + 1, close) + "]", state);
					i = close + 1;
					continue;
				}
			}

			String delimiter = matchMarkdownDelimiter(text, i);
			if (delimiter != null) {
				int close = findClosingDelimiter(text, i + delimiter.length(), delimiter);
				if (close > i) {
					appendPlainSegment(segments, plain, state);
					MarkdownState nestedState = applyDelimiterStyle(state, delimiter);
					segments.addAll(parseNestedMarkdown(text.substring(i + delimiter.length(), close), nestedState));
					i = close + delimiter.length();
					continue;
				}
			}

			plain.append(text.charAt(i));
			i++;
		}
		appendPlainSegment(segments, plain, state);
		return segments;
	}

	private static String matchMarkdownDelimiter(String text, int index) {
		for (String delimiter : MARKDOWN_DELIMITERS) {
			if (text.startsWith(delimiter, index)) {
				if (MessageParserCommon.isUnderscoreDelimiter(delimiter)
						&& MessageParserCommon.isInsideDiscordAliasEmoji(text, index, DISCORD_ALIAS_EMOJI_PATTERN)) {
					continue;
				}
				return delimiter;
			}
		}
		return null;
	}

	private static int findClosingDelimiter(String text, int start, String delimiter) {
		for (int i = start; i <= text.length() - delimiter.length(); i++) {
			if (text.charAt(i) == '\\') {
				i++;
				continue;
			}
			if (MessageParserCommon.isUnderscoreDelimiter(delimiter)
					&& MessageParserCommon.isInsideDiscordAliasEmoji(text, i, DISCORD_ALIAS_EMOJI_PATTERN)) {
				continue;
			}
			if (text.startsWith(delimiter, i)) {
				return i;
			}
		}
		return -1;
	}

	private static MarkdownState applyDelimiterStyle(MarkdownState base, String delimiter) {
		MarkdownState state = base.copy();
		switch (delimiter) {
			case "***" -> {
				state.bold = true;
				state.italic = true;
			}
			case "**" -> state.bold = true;
			case "*", "_" -> state.italic = true;
			case "__" -> state.underlined = true;
			case "~~" -> state.strikethrough = true;
			case "||" -> state.obfuscated = true;
			default -> {
			}
		}
		return state;
	}

	private static void appendPlainSegment(List<TextSegment> segments, StringBuilder plain, MarkdownState state) {
		if (plain.isEmpty()) {
			return;
		}
		addStyledSegment(segments, plain.toString(), state);
		plain.setLength(0);
	}

	private static void addStyledSegment(List<TextSegment> segments, String text, MarkdownState state) {
		if (text.isEmpty()) {
			return;
		}
		TextSegment segment = new TextSegment(text);
		segment.bold = state.bold;
		segment.italic = state.italic;
		segment.underlined = state.underlined;
		segment.strikethrough = state.strikethrough;
		segment.obfuscated = state.obfuscated;
		if (state.color != null) {
			segment.color = state.color;
		}
		if (segment.obfuscated) {
			segment.hoverText = text;
		}
		segments.add(segment);
	}

	private static List<TextSegment> postProcessInlineSegments(List<TextSegment> segments,
	                                                           Message message,
	                                                           boolean parseMentions,
	                                                           boolean parseTimestamps,
	                                                           boolean parseCustomEmojis,
	                                                           boolean parseUnicodeEmojis,
	                                                           boolean parseHyperlinks) {
		if ((!parseMentions && !parseTimestamps && !parseCustomEmojis && !parseUnicodeEmojis && !parseHyperlinks) || segments.isEmpty()) {
			return segments;
		}
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.text == null || segment.text.isEmpty() || segment.clickUrl != null) {
				out.add(segment);
				continue;
			}
			List<TextSegment> current = List.of(segment);
			if (parseMentions && message != null) {
				current = splitSegmentsByUserMention(current, message);
				current = splitSegmentsByRoleMention(current, message);
				current = splitSegmentsByChannelMention(current, message);
				current = splitSegmentsByEveryoneHereMention(current, message);
			}
			if (parseTimestamps) {
				current = MessageParserCommon.splitSegmentsByTimestamp(current);
			}
			if (parseHyperlinks) {
				current = MessageParserCommon.splitSegmentsByMarkdownLink(current);
				current = MessageParserCommon.splitSegmentsByBareUrl(current);
			}
			if (parseCustomEmojis) {
				current = splitSegmentsByCustomEmoji(current);
				current = splitSegmentsByDiscordAliasEmoji(current);
			}
			if (parseUnicodeEmojis) {
				current = MessageParserCommon.splitSegmentsByUnicodeEmoji(current);
			}
			for (TextSegment seg : current) {
				if (seg.obfuscated && seg.clickUrl == null && (seg.hoverText == null || seg.hoverText.isEmpty())) {
					seg.hoverText = seg.text;
				}
				out.add(seg);
			}
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByUserMention(List<TextSegment> segments, Message message) {
		return MessageParserCommon.splitSegments(segments, USER_MENTION_PATTERN, (segment, matcher) -> {
			String userId = matcher.group(1);
			String displayName = userId;
			String color = "white";
			for (User user : message.getMentions().getUsers()) {
				if (user.getId().equals(userId)) {
					Member member = message.getGuild().getMember(user);
					displayName = member != null ? member.getEffectiveName() : user.getName();
					color = colorOrDefault(getRoleColorHex(member));
					break;
				}
			}
			TextSegment mention = TextSegmentUtils.copySegment(segment, "[@" + displayName + "]");
			mention.color = color;
			return mention;
		});
	}

	private static List<TextSegment> splitSegmentsByRoleMention(List<TextSegment> segments, Message message) {
		return MessageParserCommon.splitSegments(segments, ROLE_MENTION_PATTERN, (segment, matcher) -> {
			String roleId = matcher.group(1);
			String roleName = roleId;
			String color = "white";
			for (Role role : message.getMentions().getRoles()) {
				if (role.getId().equals(roleId)) {
					roleName = role.getName();
					Color roleColor = role.getColors().getPrimary();
					if (roleColor != null) {
						color = "#%06X".formatted(roleColor.getRGB() & 0xFFFFFF);
					}
					break;
				}
			}
			TextSegment mention = TextSegmentUtils.copySegment(segment, "[@" + roleName + "]");
			mention.color = color;
			return mention;
		});
	}

	private static List<TextSegment> splitSegmentsByChannelMention(List<TextSegment> segments, Message message) {
		return MessageParserCommon.splitSegments(segments, CHANNEL_MENTION_PATTERN, (segment, matcher) -> {
			String channelId = matcher.group(1);
			String channelName = channelId;
			for (GuildChannel channel : message.getMentions().getChannels()) {
				if (channel.getId().equals(channelId)) {
					channelName = channel.getName();
					break;
				}
			}
			TextSegment mention = TextSegmentUtils.copySegment(segment, "[#" + channelName + "]");
			mention.color = "yellow";
			return mention;
		});
	}

	private static List<TextSegment> splitSegmentsByEveryoneHereMention(List<TextSegment> segments, Message message) {
		if (!message.getMentions().mentionsEveryone()) {
			return segments;
		}
		return MessageParserCommon.splitSegments(segments, EVERYONE_HERE_PATTERN, (segment, matcher) -> {
			TextSegment mention = TextSegmentUtils.copySegment(segment, "[@" + matcher.group(1) + "]");
			mention.color = "yellow";
			return mention;
		});
	}

	private static List<TextSegment> splitSegmentsByCustomEmoji(List<TextSegment> segments) {
		return MessageParserCommon.splitSegments(segments, CUSTOM_EMOJI_PATTERN, (segment, matcher) -> {
			TextSegment emojiSegment = TextSegmentUtils.copySegment(segment, ":" + matcher.group(1) + ":");
			emojiSegment.color = "yellow";
			return emojiSegment;
		});
	}

	private static List<TextSegment> splitSegmentsByDiscordAliasEmoji(List<TextSegment> segments) {
		return MessageParserCommon.splitSegments(segments, DISCORD_ALIAS_EMOJI_PATTERN, (segment, matcher) -> {
			String alias = matcher.group();
			if (EmojiManager.getByDiscordAlias(alias).isEmpty()) {
				return null;
			}
			TextSegment emojiSegment = TextSegmentUtils.copySegment(segment, alias);
			emojiSegment.color = "yellow";
			return emojiSegment;
		});
	}

	private static void collectCodeBlockSpans(String text, List<MarkdownSpan> spans) {
		Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
		while (matcher.find()) {
			String language = matcher.group(1);
			String content = matcher.group(2).stripTrailing();
			List<TextSegment> codeSegments;

			if ("ansi".equalsIgnoreCase(language) && ConfigManager.getBoolean("message_parsing.discord_to_minecraft.ansi_code_blocks")) {
				codeSegments = parseAnsiContent(content);
			} else {
				codeSegments = new ArrayList<>();
				codeSegments.add(new TextSegment("<code lang=[" + language + "]>", false, "yellow"));
				for (String line : content.split("\n")) {
					codeSegments.add(new TextSegment("\n  " + line));
				}
				codeSegments.add(new TextSegment("\n</code>", false, "yellow"));
			}

			spans.add(new MarkdownSpan(matcher.start(), matcher.end(), content, codeSegments));
		}
	}

	private static List<TextSegment> parseAnsiContent(String content) {
		List<TextSegment> segments = new ArrayList<>();
		Matcher matcher = ANSI_ESCAPE_PATTERN.matcher(content);

		boolean bold = false;
		boolean underline = false;
		boolean strikethrough = false;
		boolean italic = false;
		String color = null;

		int cursor = 0;
		while (matcher.find()) {
			if (matcher.start() > cursor) {
				appendAnsiSegment(segments, content.substring(cursor, matcher.start()), bold, underline, strikethrough, italic, color);
			}

			String[] codes = matcher.group(1).split(";");
			for (String codeStr : codes) {
				try {
					int code = Integer.parseInt(codeStr);
					switch (code) {
						case 0 -> {
							bold = false;
							underline = false;
							strikethrough = false;
							italic = false;
							color = null;
						}
						case 1 -> bold = true;
						case 3 -> italic = true;
						case 4 -> underline = true;
						case 9 -> strikethrough = true;
						case 30 -> color = "black";
						case 31 -> color = "red";
						case 32 -> color = "green";
						case 33 -> color = "gold";
						case 34 -> color = "blue";
						case 35 -> color = "purple";
						case 36 -> color = "aqua";
						case 37 -> color = "white";
					}
				} catch (NumberFormatException ignored) {
				}
			}

			cursor = matcher.end();
		}

		if (cursor < content.length()) {
			appendAnsiSegment(segments, content.substring(cursor), bold, underline, strikethrough, italic, color);
		}

		if (segments.isEmpty()) {
			segments.add(new TextSegment(content));
		}

		return segments;
	}

	private static void appendAnsiSegment(List<TextSegment> segments, String text,
	                                      boolean bold, boolean underline, boolean strikethrough, boolean italic, String color) {
		if (text.isEmpty()) {
			return;
		}
		TextSegment seg = new TextSegment(text);
		seg.bold = bold;
		seg.underlined = underline;
		seg.strikethrough = strikethrough;
		seg.italic = italic;
		if (color != null) {
			seg.color = color;
		}
		segments.add(seg);
	}

	private static boolean isSpoilerWrappedUrl(String raw, String url) {
		if (raw == null || raw.isEmpty() || url == null || url.isEmpty()) {
			return false;
		}
		Matcher spoilerMatcher = SPOILER_CONTENT_PATTERN.matcher(raw);
		while (spoilerMatcher.find()) {
			String content = spoilerMatcher.group(1);
			if (content == null) {
				continue;
			}
			String normalized = SPOILER_URL_STRIP_PATTERN.matcher(content).replaceAll("");
			if (url.equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	private static List<TextSegment> buildAttachmentSegments(String type, String fileName, String url, boolean spoiler) {
		List<TextSegment> segments = new ArrayList<>();
		TextSegment prefix = new TextSegment(ATTACHMENT_LABEL_PREFIX.formatted(type), false, URL_COLOR);
		TextSegment fileNameSegment = new TextSegment(fileName, false, URL_COLOR);
		TextSegment suffix = new TextSegment(LABEL_SUFFIX, false, URL_COLOR);

		applyLinkStyle(prefix, url);
		applyLinkStyle(fileNameSegment, url);
		applyLinkStyle(suffix, url);

		if (spoiler) {
			fileNameSegment.obfuscated = true;
			fileNameSegment.hoverText = fileName;
		}

		segments.add(prefix);
		segments.add(fileNameSegment);
		segments.add(suffix);
		return segments;
	}

	private static List<TextSegment> buildEmbedSegments(String title, String url, boolean spoiler) {
		List<TextSegment> segments = new ArrayList<>();
		String color = url == null ? "yellow" : URL_COLOR;
		TextSegment prefix = new TextSegment(EMBED_LABEL_PREFIX, false, color);
		TextSegment titleSegment = new TextSegment(title, false, color);
		TextSegment suffix = new TextSegment(LABEL_SUFFIX, false, color);

		if (url != null) {
			applyLinkStyle(prefix, url);
			applyLinkStyle(titleSegment, url);
			applyLinkStyle(suffix, url);
		}

		if (spoiler) {
			titleSegment.obfuscated = true;
			titleSegment.hoverText = title;
		}

		segments.add(prefix);
		segments.add(titleSegment);
		segments.add(suffix);
		return segments;
	}

	private static void applyLinkStyle(TextSegment segment, String url) {
		segment.underlined = true;
		segment.clickUrl = url;
		if (segment.hoverText == null) {
			segment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
		}
	}

	private static void applySpoilerStyle(TextSegment segment) {
		segment.obfuscated = true;
		// Obfuscated Minecraft text is unreadable in chat, so we keep original plain text as hover preview.
		segment.hoverText = segment.text;
	}

	private static String colorOrDefault(String color) {
		return color != null ? color : "white";
	}

	private static String truncateMainRaw(String raw) {
		String lineLimited = applyMainLineLimit(raw);
		int maxLength = containsFullWidthCharacter(raw) ? MAIN_TRUNCATE_LIMIT_WIDE : MAIN_TRUNCATE_LIMIT_NARROW;
		if (lineLimited.length() <= maxLength) {
			return lineLimited;
		}
		return safeTruncate(lineLimited, maxLength) + "...";
	}

	private static String applyMainLineLimit(String raw) {
		String[] lines = raw.split("\n", -1);
		if (lines.length <= MAX_CONTENT_LINES) {
			return raw;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < MAX_CONTENT_LINES - 1; i++) {
			if (i > 0) {
				sb.append("\n");
			}
			sb.append(lines[i]);
		}
		sb.append("\n...");
		return sb.toString();
	}

	private static String truncateReplyRaw(String raw) {
		int replyLimit = containsFullWidthCharacter(raw) ? REPLY_TRUNCATE_LIMIT_WIDE : REPLY_TRUNCATE_LIMIT_NARROW;
		int newlineIndex = raw.indexOf('\n');
		int cutoff = newlineIndex >= 0 ? Math.min(newlineIndex, replyLimit) : replyLimit;
		if (cutoff == 0) {
			return "...";
		}
		if (raw.length() <= cutoff) {
			return raw;
		}
		return safeTruncate(raw, cutoff) + "...";
	}

	private static List<TextSegment> enforceSingleLine(List<TextSegment> segments) {
		List<TextSegment> result = new ArrayList<>();
		boolean cut = false;
		for (TextSegment segment : segments) {
			if (cut) {
				break;
			}
			String text = segment.text == null ? "" : segment.text;
			int newline = text.indexOf('\n');
			if (newline < 0) {
				result.add(TextSegmentUtils.copySegment(segment, text));
				continue;
			}
			if (newline > 0) {
				result.add(TextSegmentUtils.copySegment(segment, text.substring(0, newline)));
			}
			TextSegmentUtils.appendEllipsis(result);
			cut = true;
		}
		return result;
	}

	private static boolean containsFullWidthCharacter(String text) {
		for (int i = 0; i < text.length(); ) {
			int codePoint = text.codePointAt(i);
			Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
			if (script == Character.UnicodeScript.HAN
					|| script == Character.UnicodeScript.HIRAGANA
					|| script == Character.UnicodeScript.KATAKANA
					|| script == Character.UnicodeScript.HANGUL) {
				return true;
			}
			Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
			if (block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
					|| block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
				return true;
			}
			i += Character.charCount(codePoint);
		}
		return false;
	}

	private static String replacePlaceholders(String text, String effectiveName, String roleColor) {
		String serverName = getServerName();
		String serverColor = getServerColor();

		return text.replace("{server}", serverName)
				.replace("{server_color}", serverColor)
				.replace("{effective_name}", effectiveName)
				.replace("{role_color}", roleColor);
	}

	/**
	 * Gets the hex color string for a member's highest colored role.
	 *
	 * @param member The Discord member (may be null).
	 * @return The hex color string (e.g. "#FF0000"), or "white" if no role color.
	 */
	public static String getRoleColorHex(Member member) {
		if (member == null) {
			return "white";
		}
		Color color = member.getColors().getPrimary();
		if (color == null) {
			return "white";
		}
		return "#%06X".formatted(color.getRGB() & 0xFFFFFF);
	}

	private static String getServerName() {
		return "Discord";
	}

	private static String getServerColor() {
		return "blue";
	}

	private static String safeTruncate(String text, int maxLen) {
		if (text.length() <= maxLen) {
			return text;
		}
		if (Character.isHighSurrogate(text.charAt(maxLen - 1))) {
			return text.substring(0, maxLen - 1);
		}
		return text.substring(0, maxLen);
	}

	private record TokenSpan(int start, int end, TextSegment segment) implements Span {
	}

	private record MarkdownSpan(int start, int end, String innerText, List<TextSegment> codeBlockSegments) implements Span {
	}
}
