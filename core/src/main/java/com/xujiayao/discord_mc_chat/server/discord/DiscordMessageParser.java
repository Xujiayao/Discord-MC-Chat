package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class DiscordMessageParser {

	// Discord markdown patterns
	private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("(?<!\\\\)_(.+?)(?<!\\\\)_");
	private static final Pattern ITALIC_ASTERISK_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
	private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__(.+?)__");
	private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
	private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
	private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:\\w*\\n)?([\\s\\S]*?)```");
	private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");

	// Discord mention patterns in raw content
	private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");

	// Discord custom emoji patterns
	private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:(\\w+):\\d+>");

	// Unicode emoji pattern (basic, covering common emoji ranges)
	private static final Pattern UNICODE_EMOJI_PATTERN = Pattern.compile(
			"[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
					"[\\x{1F1E0}-\\x{1F1FF}]|[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}]|" +
					"[\\x{FE00}-\\x{FE0F}]|[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA00}-\\x{1FA6F}]|" +
					"[\\x{1FA70}-\\x{1FAFF}]|[\\x{200D}]|[\\x{20E3}]|" +
					"[\\x{231A}-\\x{231B}]|[\\x{23E9}-\\x{23F3}]|[\\x{23F8}-\\x{23FA}]|" +
					"[\\x{25AA}-\\x{25AB}]|[\\x{25B6}]|[\\x{25C0}]|[\\x{25FB}-\\x{25FE}]|" +
					"[\\x{2614}-\\x{2615}]|[\\x{2648}-\\x{2653}]|[\\x{267F}]|[\\x{2693}]|" +
					"[\\x{26A1}]|[\\x{26AA}-\\x{26AB}]|[\\x{26BD}-\\x{26BE}]|" +
					"[\\x{26C4}-\\x{26C5}]|[\\x{26CE}]|[\\x{26D4}]|[\\x{26EA}]|" +
					"[\\x{26F2}-\\x{26F3}]|[\\x{26F5}]|[\\x{26FA}]|[\\x{26FD}]|" +
					"[\\x{2702}]|[\\x{2705}]|[\\x{2708}-\\x{270D}]|[\\x{270F}]"
	);

	// Hyperlink pattern: [text](url) or bare URLs
	private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");
	private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://\\S+)");

	/**
	 * Builds the main message line segments for a Discord chat message.
	 * <p>
	 * The format follows the custom_messages {@code common.chat} pattern:
	 * [server] &lt;effective_name&gt; {parsed message content}
	 *
	 * @param message The Discord message.
	 * @return The list of text segments for the main message line.
	 */
	public static List<TextSegment> buildChatSegments(Message message) {
		List<TextSegment> segments = new ArrayList<>();

		Member member = message.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = getRoleColorHex(member);

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			// Fallback: build a simple segment
			segments.add(new TextSegment("<" + effectiveName + "> ", false, roleColor));
			segments.addAll(parseMessageContent(message));
			return segments;
		}

		// Build segments from common.chat template
		JsonNode chatNode = customMessages.path("common").path("chat");
		if (chatNode.isArray()) {
			for (JsonNode segNode : chatNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				// Replace placeholders
				text = replacePlaceholders(text, message, effectiveName, roleColor);
				color = replacePlaceholders(color, message, effectiveName, roleColor);

				if (text.contains("{message}")) {
					// Split around {message} and inject parsed message content
					String[] parts = text.split("\\{message}", -1);
					if (!parts[0].isEmpty()) {
						segments.add(new TextSegment(parts[0], bold, color));
					}
					segments.addAll(parseMessageContent(message));
					if (parts.length > 1 && !parts[1].isEmpty()) {
						segments.add(new TextSegment(parts[1], bold, color));
					}
				} else {
					segments.add(new TextSegment(text, bold, color));
				}
			}
		} else {
			// Fallback
			segments.add(new TextSegment("<" + effectiveName + "> ", false, roleColor));
			segments.addAll(parseMessageContent(message));
		}

		return segments;
	}

	/**
	 * Builds the command notification segments for when a Discord user executes a slash command.
	 * <p>
	 * The format follows the custom_messages {@code discord_to_minecraft.command} pattern.
	 *
	 * @param effectiveName The display name of the Discord user.
	 * @param roleColor     The hex color of the user's highest role.
	 * @param commandName   The name of the slash command executed.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> buildCommandSegments(String effectiveName, String roleColor, String commandName) {
		List<TextSegment> segments = new ArrayList<>();

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			segments.add(new TextSegment("[Discord] ", true, "blue"));
			segments.add(new TextSegment(effectiveName + " ", false, roleColor));
			segments.add(new TextSegment("executed [" + commandName + "] command!", false, "gray"));
			return segments;
		}

		JsonNode commandNode = customMessages.path("discord_to_minecraft").path("command");
		if (commandNode.isArray()) {
			for (JsonNode segNode : commandNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", effectiveName)
						.replace("{role_color}", roleColor)
						.replace("{command}", commandName);
				color = color.replace("{role_color}", roleColor);

				segments.add(new TextSegment(text, bold, color));
			}
		}

		return segments;
	}

	/**
	 * Builds the reply context line segments (the ┌──── line).
	 * <p>
	 * The format follows the custom_messages {@code discord_to_minecraft.response} pattern.
	 *
	 * @param referencedMessage The message being replied to.
	 * @return The list of text segments for the reply line, or null if no reply.
	 */
	public static List<TextSegment> buildReplySegments(Message referencedMessage) {
		if (referencedMessage == null) {
			return null;
		}

		List<TextSegment> segments = new ArrayList<>();

		Member refMember = referencedMessage.getMember();
		String refName = refMember != null ? refMember.getEffectiveName() : referencedMessage.getAuthor().getName();
		String refRoleColor = getRoleColorHex(refMember);
		String refContent = referencedMessage.getContentDisplay();
		if (refContent.length() > 50) {
			refContent = safeTruncate(refContent, 50) + "...";
		}

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			segments.add(new TextSegment("    \u250C\u2500\u2500\u2500\u2500 ", true, "dark_gray"));
			segments.add(new TextSegment("<" + refName + "> ", false, refRoleColor));
			segments.add(new TextSegment(refContent, false, "dark_gray"));
			return segments;
		}

		JsonNode responseNode = customMessages.path("discord_to_minecraft").path("response");
		if (responseNode.isArray()) {
			for (JsonNode segNode : responseNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", refName)
						.replace("{message}", refContent);
				color = color.replace("{role_color}", refRoleColor);

				segments.add(new TextSegment(text, bold, color));
			}
		}

		return segments;
	}

	/**
	 * Gets the mention notification text from custom_messages.
	 *
	 * @param effectiveName The display name of the message author who mentioned someone.
	 * @return The mention notification text.
	 */
	public static String getMentionNotificationText(String effectiveName) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return effectiveName + " mentioned you!";
		}

		String template = customMessages.path("discord_to_minecraft").path("mentioned").asText("{effective_name} mentioned you!");
		return template.replace("{effective_name}", effectiveName);
	}

	/**
	 * Collects the Minecraft player UUIDs that should be notified about mentions in this message.
	 * <p>
	 * Checks both user mentions (via account linking) and role mentions (via linked accounts
	 * that have the mentioned role).
	 *
	 * @param message The Discord message.
	 * @return A set of Minecraft player UUID strings to notify.
	 */
	public static Set<String> collectMentionedPlayerUuids(Message message) {
		Set<String> uuids = new HashSet<>();

		// Direct user mentions
		for (User mentionedUser : message.getMentions().getUsers()) {
			List<String> linkedUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(mentionedUser.getId());
			uuids.addAll(linkedUuids);
		}

		// Role mentions
		for (Role mentionedRole : message.getMentions().getRoles()) {
			if (message.getGuild() != null) {
				List<Member> membersWithRole = message.getGuild().getMembersWithRoles(mentionedRole);
				for (Member m : membersWithRole) {
					List<String> linkedUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(m.getUser().getId());
					uuids.addAll(linkedUuids);
				}
			}
		}

		// @everyone / @here mentions
		if (message.getMentions().mentionsEveryone()) {
			// Notify all linked players
			// LinkedAccountManager only exposes per-Discord-ID lookups, so we iterate all known links
			// This is handled by checking all online linked players on the Minecraft side
		}

		return uuids;
	}

	/**
	 * Parses the content of a Discord message into a list of styled text segments.
	 * <p>
	 * This method handles all the Discord message content types according to the
	 * {@code message_parsing.discord_to_minecraft.*} config switches:
	 * markdown, mentions, custom_emojis, unicode_emojis, hyperlinks, attachments,
	 * stickers, embeds, and components.
	 *
	 * @param message The Discord message.
	 * @return The list of text segments representing the parsed message body.
	 */
	public static List<TextSegment> parseMessageContent(Message message) {
		List<TextSegment> segments = new ArrayList<>();
		String raw = message.getContentRaw();

		boolean parseMentions = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.mentions");
		boolean parseCustomEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.custom_emojis");
		boolean parseUnicodeEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.unicode_emojis");
		boolean parseMarkdown = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown");
		boolean parseHyperlinks = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.hyperlinks");
		boolean parseAttachments = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.attachments");
		boolean parseStickers = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.stickers");
		boolean parseEmbeds = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.embeds");
		boolean parseComponents = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.components");
		boolean parseResponses = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.responses");

		// Process the raw text content
		if (!raw.isEmpty()) {
			segments.addAll(parseRawContent(raw, message, parseMentions, parseCustomEmojis,
					parseUnicodeEmojis, parseMarkdown, parseHyperlinks));
		}

		// Append attachments
		if (parseAttachments) {
			for (Message.Attachment attachment : message.getAttachments()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String type = attachment.isImage() ? "image" : "file";
				String label = "<attachment type=\"" + type + "\" name=\"" + attachment.getFileName() + "\">";

				TextSegment seg = new TextSegment(label, false, "#3366CC");
				seg.underlined = true;
				seg.clickUrl = attachment.getUrl();
				seg.hoverText = attachment.getUrl();
				segments.add(seg);
			}
		}

		// Append stickers
		if (parseStickers) {
			for (StickerItem sticker : message.getStickers()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				segments.add(new TextSegment("<sticker name=\"" + sticker.getName() + "\">", false, "yellow"));
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
				TextSegment seg = new TextSegment("<embed title=\"" + title + "\">", false, "yellow");
				if (embed.getUrl() != null) {
					seg.clickUrl = embed.getUrl();
					seg.hoverText = embed.getUrl();
				}
				segments.add(seg);
			}
		}

		// Append interactive components indicator
		if (parseComponents && !message.getComponents().isEmpty()) {
			if (!segments.isEmpty()) {
				segments.add(new TextSegment(" "));
			}
			segments.add(new TextSegment("<interactive components>", false, "yellow"));
		}

		return segments;
	}

	/**
	 * Parses the raw text content of a Discord message, handling mentions, emojis,
	 * markdown, and hyperlinks inline.
	 *
	 * @param raw               The raw content string.
	 * @param message           The Discord message (for resolving mentions).
	 * @param parseMentions     Whether to parse mentions.
	 * @param parseCustomEmojis Whether to parse custom emojis.
	 * @param parseUnicodeEmojis Whether to parse unicode emojis.
	 * @param parseMarkdown     Whether to parse markdown formatting.
	 * @param parseHyperlinks   Whether to parse hyperlinks.
	 * @return The list of text segments for the raw content.
	 */
	private static List<TextSegment> parseRawContent(String raw, Message message,
													 boolean parseMentions, boolean parseCustomEmojis,
													 boolean parseUnicodeEmojis, boolean parseMarkdown,
													 boolean parseHyperlinks) {
		List<TextSegment> segments = new ArrayList<>();

		// First pass: resolve Discord-specific tokens (mentions, custom emojis) into display text
		// We build a list of "token ranges" that have special rendering
		List<TokenSpan> tokens = new ArrayList<>();

		if (parseMentions) {
			collectUserMentionTokens(raw, message, tokens);
			collectRoleMentionTokens(raw, message, tokens);
			collectChannelMentionTokens(raw, message, tokens);
		}

		if (parseCustomEmojis) {
			collectCustomEmojiTokens(raw, tokens);
		}

		if (parseHyperlinks) {
			collectMarkdownLinkTokens(raw, tokens);
			collectBareUrlTokens(raw, tokens);
		}

		if (parseUnicodeEmojis) {
			collectUnicodeEmojiTokens(raw, tokens);
		}

		// Sort tokens by start position
		tokens.sort((a, b) -> Integer.compare(a.start, b.start));

		// Remove overlapping tokens (keep the first one)
		tokens = removeOverlaps(tokens);

		// Build segments from the raw text, inserting special tokens
		int cursor = 0;
		for (TokenSpan token : tokens) {
			if (token.start > cursor) {
				// Plain text before this token
				String plainText = raw.substring(cursor, token.start);
				if (parseMarkdown) {
					segments.addAll(parseMarkdownText(plainText));
				} else {
					segments.add(new TextSegment(plainText));
				}
			}
			segments.add(token.segment);
			cursor = token.end;
		}

		// Remaining text after last token
		if (cursor < raw.length()) {
			String remaining = raw.substring(cursor);
			if (parseMarkdown) {
				segments.addAll(parseMarkdownText(remaining));
			} else {
				segments.add(new TextSegment(remaining));
			}
		}

		return segments;
	}

	/**
	 * Collects user mention tokens from the raw content.
	 */
	private static void collectUserMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = USER_MENTION_PATTERN.matcher(raw);
		while (matcher.find()) {
			String userId = matcher.group(1);
			// Try to resolve from the message's mentioned users
			String displayName = null;
			String color = null;
			for (User user : message.getMentions().getUsers()) {
				if (user.getId().equals(userId)) {
					Member member = message.getGuild() != null ? message.getGuild().getMember(user) : null;
					displayName = member != null ? member.getEffectiveName() : user.getName();
					color = getRoleColorHex(member);
					break;
				}
			}
			if (displayName == null) {
				displayName = userId;
			}
			TextSegment seg = new TextSegment("[@" + displayName + "]", false, color != null ? color : "white");
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects role mention tokens from the raw content.
	 */
	private static void collectRoleMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = ROLE_MENTION_PATTERN.matcher(raw);
		while (matcher.find()) {
			String roleId = matcher.group(1);
			String roleName = roleId;
			String color = "white";
			for (Role role : message.getMentions().getRoles()) {
				if (role.getId().equals(roleId)) {
					roleName = role.getName();
					Color roleColor = role.getColor();
					if (roleColor != null) {
						color = String.format("#%06X", roleColor.getRGB() & 0xFFFFFF);
					}
					break;
				}
			}
			TextSegment seg = new TextSegment("[@" + roleName + "]", false, color);
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects channel mention tokens from the raw content.
	 */
	private static void collectChannelMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = CHANNEL_MENTION_PATTERN.matcher(raw);
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
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects custom emoji tokens from the raw content.
	 */
	private static void collectCustomEmojiTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = CUSTOM_EMOJI_PATTERN.matcher(raw);
		while (matcher.find()) {
			String emojiName = matcher.group(1);
			TextSegment seg = new TextSegment(":" + emojiName + ":", false, "yellow");
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects markdown-style link tokens from the raw content.
	 */
	private static void collectMarkdownLinkTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(raw);
		while (matcher.find()) {
			String linkText = matcher.group(1);
			String url = matcher.group(2);
			TextSegment seg = new TextSegment(linkText, false, "#3366CC");
			seg.underlined = true;
			seg.clickUrl = url;
			seg.hoverText = url;
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects bare URL tokens from the raw content.
	 */
	private static void collectBareUrlTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = BARE_URL_PATTERN.matcher(raw);
		while (matcher.find()) {
			String url = matcher.group(1);
			TextSegment seg = new TextSegment(url, false, "#3366CC");
			seg.underlined = true;
			seg.clickUrl = url;
			seg.hoverText = url;
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects unicode emoji tokens from the raw content and converts them to
	 * short-code format (e.g. :blush:).
	 */
	private static void collectUnicodeEmojiTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = UNICODE_EMOJI_PATTERN.matcher(raw);
		while (matcher.find()) {
			String emoji = matcher.group();
			String shortCode = EmojiShortCodeMapper.getShortCode(emoji);
			TextSegment seg = new TextSegment(shortCode, false, "yellow");
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Removes overlapping token spans, keeping the first (earlier start) token.
	 *
	 * @param tokens The sorted list of tokens.
	 * @return A list with overlaps removed.
	 */
	private static List<TokenSpan> removeOverlaps(List<TokenSpan> tokens) {
		List<TokenSpan> result = new ArrayList<>();
		int lastEnd = -1;
		for (TokenSpan token : tokens) {
			if (token.start >= lastEnd) {
				result.add(token);
				lastEnd = token.end;
			}
		}
		return result;
	}

	/**
	 * Parses simple markdown formatting in plain text into styled segments.
	 * <p>
	 * Handles: bold (**), italic (* or _), underline (__), strikethrough (~~),
	 * spoiler (||), code blocks (```), and inline code (`).
	 *
	 * @param text The plain text that may contain markdown.
	 * @return The list of text segments with markdown styles applied.
	 */
	private static List<TextSegment> parseMarkdownText(String text) {
		List<TextSegment> segments = new ArrayList<>();

		// Process code blocks first (they take priority and should not be further parsed)
		List<MarkdownSpan> spans = new ArrayList<>();
		collectMarkdownSpans(text, CODE_BLOCK_PATTERN, MarkdownType.CODE_BLOCK, spans);
		collectMarkdownSpans(text, INLINE_CODE_PATTERN, MarkdownType.INLINE_CODE, spans);
		collectMarkdownSpans(text, BOLD_PATTERN, MarkdownType.BOLD, spans);
		collectMarkdownSpans(text, UNDERLINE_PATTERN, MarkdownType.UNDERLINE, spans);
		collectMarkdownSpans(text, STRIKETHROUGH_PATTERN, MarkdownType.STRIKETHROUGH, spans);
		collectMarkdownSpans(text, SPOILER_PATTERN, MarkdownType.SPOILER, spans);
		collectMarkdownSpans(text, ITALIC_UNDERSCORE_PATTERN, MarkdownType.ITALIC, spans);
		collectMarkdownSpans(text, ITALIC_ASTERISK_PATTERN, MarkdownType.ITALIC, spans);

		// Sort by start position and remove overlaps
		spans.sort((a, b) -> Integer.compare(a.start, b.start));
		spans = removeMarkdownOverlaps(spans);

		int cursor = 0;
		for (MarkdownSpan span : spans) {
			if (span.start > cursor) {
				segments.add(new TextSegment(text.substring(cursor, span.start)));
			}

			TextSegment seg = new TextSegment(span.innerText);
			switch (span.type) {
				case BOLD -> seg.bold = true;
				case ITALIC -> seg.italic = true;
				case UNDERLINE -> seg.underlined = true;
				case STRIKETHROUGH -> seg.strikethrough = true;
				case SPOILER -> seg.obfuscated = true;
				case CODE_BLOCK, INLINE_CODE -> seg.color = "gray";
			}
			segments.add(seg);

			cursor = span.end;
		}

		if (cursor < text.length()) {
			segments.add(new TextSegment(text.substring(cursor)));
		}

		return segments;
	}

	/**
	 * Collects markdown spans from the text for a given pattern and type.
	 */
	private static void collectMarkdownSpans(String text, Pattern pattern, MarkdownType type, List<MarkdownSpan> spans) {
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			spans.add(new MarkdownSpan(matcher.start(), matcher.end(), matcher.group(1), type));
		}
	}

	/**
	 * Removes overlapping markdown spans, keeping the first one.
	 */
	private static List<MarkdownSpan> removeMarkdownOverlaps(List<MarkdownSpan> spans) {
		List<MarkdownSpan> result = new ArrayList<>();
		int lastEnd = -1;
		for (MarkdownSpan span : spans) {
			if (span.start >= lastEnd) {
				result.add(span);
				lastEnd = span.end;
			}
		}
		return result;
	}

	/**
	 * Replaces template placeholders in a custom_messages text with actual values.
	 *
	 * @param text          The template text.
	 * @param message       The Discord message.
	 * @param effectiveName The user's effective display name.
	 * @param roleColor     The user's role color hex.
	 * @return The text with placeholders replaced.
	 */
	private static String replacePlaceholders(String text, Message message, String effectiveName, String roleColor) {
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
	static String getRoleColorHex(Member member) {
		if (member == null) {
			return "white";
		}
		Color color = member.getColor();
		if (color == null) {
			return "white";
		}
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/**
	 * Gets the server display name for the chat prefix.
	 * In standalone mode this is "Discord", in single_server mode it is also "Discord".
	 *
	 * @return The server name string.
	 */
	private static String getServerName() {
		return "Discord";
	}

	/**
	 * Gets the server color for the chat prefix.
	 *
	 * @return The color string.
	 */
	private static String getServerColor() {
		return "blue";
	}

	/**
	 * Internal record representing a span of raw text that maps to a special token
	 * (mention, emoji, link, etc.).
	 */
	private record TokenSpan(int start, int end, TextSegment segment) {
	}

	/**
	 * Internal record representing a markdown-formatted span of text.
	 */
	private record MarkdownSpan(int start, int end, String innerText, MarkdownType type) {
	}

	/**
	 * Types of markdown formatting.
	 */
	private enum MarkdownType {
		BOLD,
		ITALIC,
		UNDERLINE,
		STRIKETHROUGH,
		SPOILER,
		CODE_BLOCK,
		INLINE_CODE
	}

	/**
	 * Safely truncates a string to the given maximum character count without splitting
	 * surrogate pairs (multi-byte emoji characters).
	 *
	 * @param text   The text to truncate.
	 * @param maxLen The maximum number of characters.
	 * @return The truncated text.
	 */
	private static String safeTruncate(String text, int maxLen) {
		if (text.length() <= maxLen) {
			return text;
		}
		// Avoid splitting a surrogate pair
		if (Character.isHighSurrogate(text.charAt(maxLen - 1))) {
			return text.substring(0, maxLen - 1);
		}
		return text.substring(0, maxLen);
	}
}
