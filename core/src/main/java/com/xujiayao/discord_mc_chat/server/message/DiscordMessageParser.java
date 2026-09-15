package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import net.fellbaum.jemoji.EmojiManager;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Discord message content into pre-built {@link TextSegment} lists for Minecraft rendering.
 * <p>
 * This class is deliberately free of JDA types: message contents arrive as plain strings plus a
 * {@link MentionResolver} and {@link MessageExtras}. The JDA-facing adapter lives in
 * {@code DiscordMessageAdapter}, which also makes the whole pipeline unit-testable with plain strings.
 * <p>
 * All parsing decisions are driven by the {@code message_parsing.discord_to_minecraft.*} config switches.
 * The server builds the full rich-text representation so that DMCC clients can directly convert the
 * segments into Minecraft Components without accessing Discord APIs or custom_messages.
 *
 * @author Xujiayao
 */
public final class DiscordMessageParser {

	/**
	 * The pseudo server name and color used for everything coming from Discord.
	 */
	public static final String SERVER_NAME = "Discord";
	public static final String SERVER_COLOR = "blue";

	private static final Pattern CODE_BLOCK = Pattern.compile("```(\\w*)\\n?([\\s\\S]*?)```");
	private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[(\\d+(?:;\\d+)*)m");

	private static final int MAX_CONTENT_LINES = 6;
	private static final int REPLY_TRUNCATE_LIMIT_WIDE = 20;
	private static final int REPLY_TRUNCATE_LIMIT_NARROW = 40;
	private static final int MAIN_TRUNCATE_LIMIT_WIDE = 200;
	private static final int MAIN_TRUNCATE_LIMIT_NARROW = 400;

	/** Embed descriptions longer than this are shortened before they are used as a fallback title. */
	private static final int EMBED_DESCRIPTION_LIMIT = 50;
	private static final int EMBED_DESCRIPTION_KEEP = 20;

	private DiscordMessageParser() {
	}

	/**
	 * The set of parsing switches for one message.
	 *
	 * @param mentions      Parse {@code <@id>} / {@code <@&id>} / {@code <#id>} mentions.
	 * @param customEmojis  Parse Discord custom and alias emoji.
	 * @param unicodeEmojis Parse Unicode emoji.
	 * @param markdown      Parse Markdown emphasis, quotes, headings and code.
	 * @param hyperlinks    Turn links into clickable segments.
	 * @param attachments   Append attachment labels.
	 * @param stickers      Append sticker labels.
	 * @param embeds        Append embed labels.
	 * @param components    Append the interactive-components indicator.
	 * @param polls         Append the poll indicator.
	 * @param timestamps    Render {@code <t:...>} timestamps.
	 * @param ansiCodeBlocks Render {@code ```ansi} blocks with their colors.
	 */
	public record Flags(boolean mentions, boolean customEmojis, boolean unicodeEmojis, boolean markdown,
						boolean hyperlinks, boolean attachments, boolean stickers, boolean embeds,
						boolean components, boolean polls, boolean timestamps, boolean ansiCodeBlocks) {

		private static final String PREFIX = "message_parsing.discord_to_minecraft.";

		/**
		 * @return The switches as configured for {@code discord_to_minecraft}.
		 */
		public static Flags fromConfig() {
			return new Flags(
					ConfigManager.getBoolean(PREFIX + "mentions"),
					ConfigManager.getBoolean(PREFIX + "custom_emojis"),
					ConfigManager.getBoolean(PREFIX + "unicode_emojis"),
					ConfigManager.getBoolean(PREFIX + "markdown"),
					ConfigManager.getBoolean(PREFIX + "hyperlinks"),
					ConfigManager.getBoolean(PREFIX + "attachments"),
					ConfigManager.getBoolean(PREFIX + "stickers"),
					ConfigManager.getBoolean(PREFIX + "embeds"),
					ConfigManager.getBoolean(PREFIX + "components"),
					ConfigManager.getBoolean(PREFIX + "polls"),
					ConfigManager.getBoolean(PREFIX + "timestamps"),
					ConfigManager.getBoolean(PREFIX + "ansi_code_blocks")
			);
		}

	}

	/**
	 * Builds the main message line segments for a Discord chat message.
	 * <p>
	 * The format follows the custom_messages {@code xxxxx_to_minecraft.user_message} pattern:
	 * {@code [server] <effective_name> message}. Multi-line messages use the YAML-style form where the
	 * header ends with {@code |} and the content follows on the next lines.
	 *
	 * @param effectiveName Display name of the author.
	 * @param roleColor     Hex color of the author's highest role.
	 * @param raw           Raw message content.
	 * @param mentions      Mention resolver for this message; null disables mention parsing.
	 * @param extras        Non-textual message parts.
	 * @param flags         Parsing switches.
	 * @return The list of text segments for the main message line.
	 */
	public static List<TextSegment> buildChatSegments(String effectiveName, String roleColor, String raw,
													  MentionResolver mentions, MessageExtras extras, Flags flags) {
		String truncatedRaw = truncateMainRaw(raw);
		MessageTemplates.Builder builder = chatTemplate()
				.with("server", SERVER_NAME)
				.with("server_color", SERVER_COLOR)
				.with("effective_name", effectiveName)
				.with("display_name", effectiveName)
				.with("role_color", roleColor)
				.content(() -> parseContent(truncatedRaw, mentions, extras, flags));
		if (truncatedRaw.contains("\n")) {
			builder.yamlMultilineHeader();
		}
		return builder.render();
	}

	/**
	 * Builds the edited message content line shown after an edit notification.
	 *
	 * @param effectiveName Display name of the author.
	 * @param roleColor     Hex color of the author's highest role.
	 * @param raw           Raw message content.
	 * @param mentions      Mention resolver for this message; null disables mention parsing.
	 * @param extras        Non-textual message parts.
	 * @param flags         Parsing switches.
	 * @return The list of text segments for the edited message content line.
	 */
	public static List<TextSegment> buildEditedMessageSegments(String effectiveName, String roleColor, String raw,
															   MentionResolver mentions, MessageExtras extras, Flags flags) {
		String truncatedRaw = truncateMainRaw(raw);
		return template("discord_to_minecraft", "edited_message")
				.with("effective_name", effectiveName)
				.with("role_color", roleColor)
				.content(() -> parseContent(truncatedRaw, mentions, extras, flags))
				.render();
	}

	/**
	 * Builds the reply context line segments (the {@code ┌────} line).
	 *
	 * @param refName     Display name of the referenced message author.
	 * @param refRoleColor Hex color of the referenced message author's highest role.
	 * @param refRaw      Raw content of the referenced message; null means there is no reply.
	 * @param mentions    Mention resolver for the referenced message; null disables mention parsing.
	 * @param extras      Non-textual parts of the referenced message.
	 * @param flags       Parsing switches.
	 * @return The list of text segments for the reply line, or null when {@code refRaw} is null.
	 */
	public static List<TextSegment> buildReplySegments(String refName, String refRoleColor, String refRaw,
													   MentionResolver mentions, MessageExtras extras, Flags flags) {
		if (refRaw == null) {
			return null;
		}
		String truncatedRaw = truncateReplyRaw(refRaw);
		return template("discord_to_minecraft", "response")
				.with("effective_name", refName)
				.with("role_color", refRoleColor)
				.content(() -> enforceSingleLine(parseContent(truncatedRaw, mentions, extras, flags)))
				.render();
	}

	/**
	 * Builds reply context segments when the referenced message is no longer available, so neither its
	 * mentions nor its attachments can be resolved.
	 *
	 * @param refName      Display name of the referenced message author.
	 * @param refRoleColor Hex color of the referenced message author's highest role.
	 * @param refRaw       Raw content of the referenced message.
	 * @param flags        Parsing switches.
	 * @return The list of text segments for the reply line.
	 */
	public static List<TextSegment> buildDetachedReplySegments(String refName, String refRoleColor, String refRaw,
															   Flags flags) {
		String truncatedRaw = truncateReplyRaw(refRaw);
		return template("discord_to_minecraft", "response")
				.with("effective_name", refName)
				.with("role_color", refRoleColor)
				.content(() -> enforceSingleLine(parseDetachedContent(truncatedRaw, flags)))
				.render();
	}

	/**
	 * Parses raw content without any message context: mentions and all non-textual parts are unavailable.
	 *
	 * @param raw   Raw content.
	 * @param flags Parsing switches.
	 * @return The parsed segments.
	 */
	public static List<TextSegment> parseDetachedContent(String raw, Flags flags) {
		if (raw == null || raw.isEmpty()) {
			return new ArrayList<>();
		}
		return parseRawContent(raw, null, flags);
	}

	/**
	 * Builds segments for a slash-command notification.
	 *
	 * @param effectiveName Display name of the Discord user.
	 * @param roleColor     Hex color of the user's highest role.
	 * @param commandName   Name of the slash command executed.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> buildCommandSegments(String effectiveName, String roleColor, String commandName) {
		return template("discord_to_minecraft", "command")
				.with("effective_name", effectiveName)
				.with("role_color", roleColor)
				.with("command", commandName)
				.render();
	}

	/**
	 * Builds segments for a reaction event.
	 *
	 * @param reactorName Display name of the user who reacted.
	 * @param roleColor   Hex color of the reactor's highest role.
	 * @param emojiText   The emoji display text (e.g. {@code :test:}).
	 * @return The list of text segments.
	 */
	public static List<TextSegment> buildReactionSegments(String reactorName, String roleColor, String emojiText) {
		return template("discord_to_minecraft", "reaction")
				.with("effective_name", reactorName)
				.with("role_color", roleColor)
				.with("emoji", emojiText)
				.render();
	}

	/**
	 * Builds segments for a message edit notification line.
	 *
	 * @param editorName Display name of the user who edited.
	 * @param roleColor  Hex color of the editor's highest role.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> buildEditNotificationSegments(String editorName, String roleColor) {
		return template("discord_to_minecraft", "edit")
				.with("effective_name", editorName)
				.with("role_color", roleColor)
				.render();
	}

	/**
	 * Builds segments for a message delete notification.
	 *
	 * @param deleterName Display name of the user who deleted.
	 * @param roleColor   Hex color of the deleter's highest role.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> buildDeleteSegments(String deleterName, String roleColor) {
		return template("discord_to_minecraft", "delete")
				.with("effective_name", deleterName)
				.with("role_color", roleColor)
				.render();
	}

	/**
	 * Parses message content into styled segments, appending the labels for attachments, stickers, embeds,
	 * interactive components and polls.
	 *
	 * @param raw      Raw content, possibly already truncated.
	 * @param mentions Mention resolver; null disables mention parsing.
	 * @param extras   Non-textual message parts.
	 * @param flags    Parsing switches.
	 * @return The parsed segments.
	 */
	public static List<TextSegment> parseContent(String raw, MentionResolver mentions, MessageExtras extras, Flags flags) {
		List<TextSegment> segments = new ArrayList<>();
		if (raw != null && !raw.isEmpty()) {
			segments.addAll(parseRawContent(raw, mentions, flags));
		}

		if (flags.attachments()) {
			for (MessageExtras.Attachment attachment : extras.attachments()) {
				separate(segments);
				segments.addAll(MessageParserCommon.attachment(
						attachment.type(), attachment.fileName(), attachment.url(), attachment.spoiler()));
			}
		}
		if (flags.stickers()) {
			for (String sticker : extras.stickers()) {
				separate(segments);
				segments.add(new TextSegment("<sticker name=[" + sticker + "]>", false, "yellow"));
			}
		}
		if (flags.embeds()) {
			for (MessageExtras.Embed embed : extras.embeds()) {
				separate(segments);
				segments.addAll(MessageParserCommon.embed(embedTitle(embed), embed.url(),
						isSpoilerWrappedUrl(raw, embed.url())));
			}
		}
		if (flags.components() && extras.hasComponents()) {
			separate(segments);
			segments.add(new TextSegment("<components>", false, "yellow"));
		}
		if (flags.polls() && extras.pollQuestion() != null) {
			separate(segments);
			segments.add(new TextSegment("<poll question=[" + extras.pollQuestion() + "]>", false, "yellow"));
		}
		return segments;
	}

	/**
	 * Replaces Discord timestamp tokens (e.g. {@code <t:1234567890:R>}) with localized plain text.
	 *
	 * @param text Source text that may contain Discord timestamp tokens.
	 * @return Text with Discord timestamp tokens replaced by human-readable values.
	 */
	public static String formatDiscordTimestampsForPlainText(String text) {
		return MessageParserCommon.formatTimestampsForPlainText(text);
	}

	/**
	 * Resolves the display title of an embed, falling back to a shortened description.
	 */
	private static String embedTitle(MessageExtras.Embed embed) {
		String title = embed.title() != null ? embed.title() : "";
		if (!title.isEmpty() || embed.description() == null) {
			return title;
		}
		String description = embed.description();
		if (description.length() > EMBED_DESCRIPTION_LIMIT) {
			description = safeTruncate(description, EMBED_DESCRIPTION_KEEP) + "...";
		}
		return description;
	}

	private static void separate(List<TextSegment> segments) {
		if (!segments.isEmpty()) {
			segments.add(new TextSegment(" "));
		}
	}

	/**
	 * @return Whether the embed URL appears inside a {@code ||spoiler||} run in the raw content.
	 */
	private static boolean isSpoilerWrappedUrl(String raw, String url) {
		if (raw == null || raw.isEmpty() || url == null || url.isEmpty()) {
			return false;
		}
		Matcher spoilerMatcher = MessageParserCommon.SPOILER_CONTENT.matcher(raw);
		while (spoilerMatcher.find()) {
			String content = spoilerMatcher.group(1);
			if (content != null && url.equals(content.replaceAll("[*_~`\\s]", ""))) {
				return true;
			}
		}
		return false;
	}

	// --- Raw content -----------------------------------------------------------------------------

	private static List<TextSegment> parseRawContent(String raw, MentionResolver mentions, Flags flags) {
		if (flags.markdown()) {
			return postProcess(parseMarkdownText(raw, flags), mentions, flags);
		}

		// Without Markdown parsing the ||spoiler|| delimiters are never consumed by the scanner, so
		// spoiler-wrapped mentions have to be matched as whole tokens. Mentions and timestamps are all
		// resolved in one left-to-right pass; nothing that was already rendered is scanned again.
		List<MessageParserCommon.TokenRule> rules = new ArrayList<>();
		if (flags.mentions() && mentions != null) {
			rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.SPOILER_USER_MENTION,
					(matcher, source) -> MessageParserCommon.spoiler(userMention(matcher.group(1), source, mentions))));
			rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.SPOILER_ROLE_MENTION,
					(matcher, source) -> MessageParserCommon.spoiler(roleMention(matcher.group(1), source, mentions))));
			rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.SPOILER_CHANNEL_MENTION,
					(matcher, source) -> MessageParserCommon.spoiler(channelMention(matcher.group(1), source, mentions))));
			if (mentions.mentionsEveryone()) {
				rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.SPOILER_EVERYONE_HERE,
						(matcher, source) -> MessageParserCommon.spoiler(everyoneMention(matcher.group(1), source))));
			}
			addMentionRules(rules, mentions);
		}
		if (flags.timestamps()) {
			rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.TIMESTAMP, MessageParserCommon::timestamp));
		}

		List<TextSegment> segments = new ArrayList<>();
		segments.add(new TextSegment(raw));
		return applyInlinePasses(MessageParserCommon.splitByRules(segments, rules), flags);
	}

	/**
	 * Applies the inline token passes to already-styled segments, in the order the historical
	 * implementation used.
	 */
	private static List<TextSegment> postProcess(List<TextSegment> segments, MentionResolver mentions, Flags flags) {
		if ((!flags.mentions() && !flags.timestamps() && !hasInlinePasses(flags)) || segments.isEmpty()) {
			return segments;
		}
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (!MessageParserCommon.isSplittable(segment)) {
				out.add(segment);
				continue;
			}
			List<TextSegment> current = List.of(segment);
			if (flags.mentions() && mentions != null) {
				current = splitMentions(current, mentions);
			}
			if (flags.timestamps()) {
				current = MessageParserCommon.splitByPattern(current, MessageParserCommon.TIMESTAMP, MessageParserCommon::timestamp);
			}
			out.addAll(withSpoilerHover(applyInlineRules(current, flags)));
		}
		return out;
	}

	/**
	 * Applies links and emoji to segments whose mentions and timestamps are already rendered.
	 */
	private static List<TextSegment> applyInlinePasses(List<TextSegment> segments, Flags flags) {
		if (!hasInlinePasses(flags) || segments.isEmpty()) {
			return segments;
		}
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (!MessageParserCommon.isSplittable(segment)) {
				out.add(segment);
				continue;
			}
			out.addAll(withSpoilerHover(applyInlineRules(List.of(segment), flags)));
		}
		return out;
	}

	private static boolean hasInlinePasses(Flags flags) {
		return flags.customEmojis() || flags.unicodeEmojis() || flags.hyperlinks();
	}

	private static List<TextSegment> applyInlineRules(List<TextSegment> segments, Flags flags) {
		List<TextSegment> current = segments;
		if (flags.hyperlinks()) {
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.MARKDOWN_LINK,
					(matcher, source) -> MessageParserCommon.link(source, matcher.group(1), matcher.group(2)));
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.BARE_URL,
					(matcher, source) -> MessageParserCommon.link(source, matcher.group(1), matcher.group(1)));
		}
		if (flags.customEmojis()) {
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.CUSTOM_EMOJI, DiscordMessageParser::customEmoji);
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.ALIAS_EMOJI,
					(matcher, source) -> emojiByAlias(matcher.group(1), source));
		}
		if (flags.unicodeEmojis()) {
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.UNICODE_EMOJI, MessageParserCommon::unicodeEmoji);
		}
		return current;
	}

	/**
	 * Obfuscated text is unreadable in game, so obfuscated segments that are not links keep their plain
	 * text as hover preview.
	 */
	private static List<TextSegment> withSpoilerHover(List<TextSegment> segments) {
		for (TextSegment segment : segments) {
			if (segment.obfuscated && segment.clickUrl == null
					&& (segment.hoverText == null || segment.hoverText.isEmpty())) {
				segment.hoverText = segment.text;
			}
		}
		return segments;
	}

	/**
	 * Appends the plain mention rules to a token table.
	 */
	private static void addMentionRules(List<MessageParserCommon.TokenRule> rules, MentionResolver mentions) {
		rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.USER_MENTION,
				(matcher, source) -> userMention(matcher.group(1), source, mentions)));
		rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.ROLE_MENTION,
				(matcher, source) -> roleMention(matcher.group(1), source, mentions)));
		rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.CHANNEL_MENTION,
				(matcher, source) -> channelMention(matcher.group(1), source, mentions)));
		if (mentions.mentionsEveryone()) {
			rules.add(new MessageParserCommon.TokenRule(MessageParserCommon.EVERYONE_HERE,
					(matcher, source) -> everyoneMention(matcher.group(1), source)));
		}
	}

	/**
	 * Runs the user, role, channel and everyone mention passes.
	 */
	private static List<TextSegment> splitMentions(List<TextSegment> segments, MentionResolver mentions) {
		List<TextSegment> current = MessageParserCommon.splitByPattern(segments, MessageParserCommon.USER_MENTION,
				(matcher, source) -> userMention(matcher.group(1), source, mentions));
		current = MessageParserCommon.splitByPattern(current, MessageParserCommon.ROLE_MENTION,
				(matcher, source) -> roleMention(matcher.group(1), source, mentions));
		current = MessageParserCommon.splitByPattern(current, MessageParserCommon.CHANNEL_MENTION,
				(matcher, source) -> channelMention(matcher.group(1), source, mentions));
		if (mentions.mentionsEveryone()) {
			current = MessageParserCommon.splitByPattern(current, MessageParserCommon.EVERYONE_HERE,
					(matcher, source) -> everyoneMention(matcher.group(1), source));
		}
		return current;
	}

	private static TextSegment userMention(String id, TextSegment source, MentionResolver mentions) {
		MentionResolver.Mention mention = mentions.user(id);
		TextSegment segment = TextSegment.copyOf(source, "[@" + (mention != null ? mention.name() : id) + "]");
		segment.color = mention != null && mention.color() != null ? mention.color() : "white";
		return segment;
	}

	private static TextSegment roleMention(String id, TextSegment source, MentionResolver mentions) {
		MentionResolver.Mention mention = mentions.role(id);
		TextSegment segment = TextSegment.copyOf(source, "[@" + (mention != null ? mention.name() : id) + "]");
		segment.color = mention != null && mention.color() != null ? mention.color() : "white";
		return segment;
	}

	private static TextSegment channelMention(String id, TextSegment source, MentionResolver mentions) {
		String name = mentions.channel(id);
		TextSegment segment = TextSegment.copyOf(source, "[#" + (name != null ? name : id) + "]");
		segment.color = "yellow";
		return segment;
	}

	private static TextSegment everyoneMention(String keyword, TextSegment source) {
		TextSegment segment = TextSegment.copyOf(source, "[@" + keyword + "]");
		segment.color = "yellow";
		return segment;
	}

	/**
	 * @return The {@code :alias:} token styled as an emoji, or null when the alias is not a known emoji.
	 */
	private static TextSegment emojiByAlias(String alias, TextSegment source) {
		if (EmojiManager.getByDiscordAlias(":" + alias + ":").isEmpty()) {
			return null;
		}
		TextSegment segment = TextSegment.copyOf(source, ":" + alias + ":");
		segment.color = "yellow";
		return segment;
	}

	/**
	 * @return The {@code <:name:id>} token rewritten as the {@code :name:} alias form.
	 */
	private static TextSegment customEmoji(Matcher matcher, TextSegment source) {
		TextSegment segment = TextSegment.copyOf(source, ":" + matcher.group(1) + ":");
		segment.color = "yellow";
		return segment;
	}

	// --- Markdown --------------------------------------------------------------------------------

	private static List<TextSegment> parseMarkdownText(String text, Flags flags) {
		List<TextSegment> segments = new ArrayList<>();
		List<CodeBlockSpan> spans = new ArrayList<>();
		collectCodeBlockSpans(text, spans, flags);
		spans.sort(Comparator.comparingInt(CodeBlockSpan::start));

		int cursor = 0;
		int lastEnd = -1;
		for (CodeBlockSpan span : spans) {
			if (span.start() < lastEnd) {
				continue;
			}
			lastEnd = span.end();
			if (span.start() > cursor) {
				segments.addAll(MarkdownParser.parseDiscordMarkup(text.substring(cursor, span.start())));
			}
			segments.addAll(span.segments());
			cursor = span.end();
		}
		if (cursor < text.length()) {
			segments.addAll(MarkdownParser.parseDiscordMarkup(text.substring(cursor)));
		}
		return segments;
	}

	private static void collectCodeBlockSpans(String text, List<CodeBlockSpan> spans, Flags flags) {
		Matcher matcher = CODE_BLOCK.matcher(text);
		while (matcher.find()) {
			String language = matcher.group(1);
			String content = matcher.group(2).stripTrailing();
			List<TextSegment> codeSegments = "ansi".equalsIgnoreCase(language) && flags.ansiCodeBlocks()
					? parseAnsiContent(content)
					: fencedCodeBlock(language, content);
			spans.add(new CodeBlockSpan(matcher.start(), matcher.end(), codeSegments));
		}
	}

	private static List<TextSegment> fencedCodeBlock(String language, String content) {
		List<TextSegment> segments = new ArrayList<>();
		segments.add(new TextSegment("<code lang=[" + language + "]>", false, "yellow"));
		for (String line : content.split("\n", 0)) {
			segments.add(new TextSegment("\n  " + line));
		}
		segments.add(new TextSegment("\n</code>", false, "yellow"));
		return segments;
	}

	/**
	 * Renders a {@code ```ansi} block by translating SGR escape sequences into segment styles.
	 */
	private static List<TextSegment> parseAnsiContent(String content) {
		List<TextSegment> segments = new ArrayList<>();
		Matcher matcher = ANSI_ESCAPE.matcher(content);

		boolean bold = false;
		boolean underline = false;
		boolean strikethrough = false;
		boolean italic = false;
		String color = null;

		int cursor = 0;
		while (matcher.find()) {
			if (matcher.start() > cursor) {
				segments.add(ansiSegment(content.substring(cursor, matcher.start()), bold, italic, underline, strikethrough, color));
			}
			for (String codeText : matcher.group(1).split(";")) {
				try {
					switch (Integer.parseInt(codeText)) {
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
						default -> {
						}
					}
				} catch (NumberFormatException ignored) {
					// Unknown SGR parameter: ignore the code and keep the current style.
				}
			}
			cursor = matcher.end();
		}

		if (cursor < content.length()) {
			segments.add(ansiSegment(content.substring(cursor), bold, italic, underline, strikethrough, color));
		}
		if (segments.isEmpty()) {
			segments.add(new TextSegment(content));
		}
		return segments;
	}

	private static TextSegment ansiSegment(String text, boolean bold, boolean italic, boolean underline,
										   boolean strikethrough, String color) {
		TextSegment segment = new TextSegment(text);
		segment.bold = bold;
		segment.italic = italic;
		segment.underlined = underline;
		segment.strikethrough = strikethrough;
		segment.color = color;
		return segment;
	}

	// --- Truncation ------------------------------------------------------------------------------

	/**
	 * Applies the main-message limits: at most {@value #MAX_CONTENT_LINES} lines, then a character limit
	 * that depends on whether the text contains full-width characters.
	 *
	 * @param raw Raw content.
	 * @return The truncated content.
	 */
	static String truncateMainRaw(String raw) {
		String lineLimited = applyMainLineLimit(raw);
		int maxLength = containsFullWidthCharacter(raw) ? MAIN_TRUNCATE_LIMIT_WIDE : MAIN_TRUNCATE_LIMIT_NARROW;
		if (lineLimited.length() <= maxLength) {
			return lineLimited;
		}
		return safeTruncate(lineLimited, maxLength) + "...";
	}

	/**
	 * Applies the reply limits: a single line, truncated at a width-dependent character count.
	 *
	 * @param raw Raw content of the referenced message.
	 * @return The truncated content.
	 */
	static String truncateReplyRaw(String raw) {
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

	/**
	 * Cuts everything from the first line break onwards and appends an ellipsis marker.
	 */
	static List<TextSegment> enforceSingleLine(List<TextSegment> segments) {
		List<TextSegment> result = new ArrayList<>();
		for (TextSegment segment : segments) {
			String text = segment.text == null ? "" : segment.text;
			int newline = text.indexOf('\n');
			if (newline < 0) {
				result.add(TextSegment.copyOf(segment, text));
				continue;
			}
			if (newline > 0) {
				result.add(TextSegment.copyOf(segment, text.substring(0, newline)));
			}
			TextSegment.appendEllipsis(result);
			return result;
		}
		return result;
	}

	/**
	 * @return Whether the text contains CJK characters, which are twice as wide in the Minecraft font.
	 */
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

	private static String safeTruncate(String text, int maxLen) {
		if (text.length() <= maxLen) {
			return text;
		}
		if (Character.isHighSurrogate(text.charAt(maxLen - 1))) {
			return text.substring(0, maxLen - 1);
		}
		return text.substring(0, maxLen);
	}

	// --- Templates -------------------------------------------------------------------------------

	private static MessageTemplates.Builder chatTemplate() {
		return template("xxxxx_to_minecraft", "user_message");
	}

	private static MessageTemplates.Builder template(String section, String key) {
		JsonNode node = I18nManager.getCustomMessages().path(section).path(key);
		return MessageTemplates.of(node);
	}

	private record CodeBlockSpan(int start, int end, List<TextSegment> segments) {
	}
}
