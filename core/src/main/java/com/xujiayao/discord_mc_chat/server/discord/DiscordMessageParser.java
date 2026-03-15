package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket.TextSegment;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.fellbaum.jemoji.EmojiManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a JDA {@link Message} into a list of {@link TextSegment}s for rendering in Minecraft.
 * <p>
 * Handles all Discord message content types: plain text, mentions (user, role, channel),
 * Unicode emojis, custom emojis, attachments, embeds, stickers, and interactive components.
 * Each content type can be individually toggled via {@code message_parsing.discord_to_minecraft.*} config keys.
 *
 * @author Xujiayao
 */
public class DiscordMessageParser {

	// Regex patterns for Discord raw content markers
	private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");
	private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:(\\w+):(\\d+)>");
	private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

	// Discord markdown patterns (simplified)
	private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("_(.+?)_");
	private static final Pattern ITALIC_ASTERISK_PATTERN = Pattern.compile("\\*(.+?)\\*");
	private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__(.+?)__");
	private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
	private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
	private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:\\w+\\n)?(.+?)```", Pattern.DOTALL);
	private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`(.+?)`");

	private static final String YELLOW = "yellow";
	private static final String LINK_COLOR = "#3366CC";

	/**
	 * Parses a Discord message into a list of styled text segments.
	 *
	 * @param message The JDA Message to parse.
	 * @return A list of TextSegments representing the message content.
	 */
	public static List<TextSegment> parse(Message message) {
		List<TextSegment> segments = new ArrayList<>();

		// Parse the text content (plain text, mentions, emojis, markdown)
		String rawContent = message.getContentRaw();
		if (!rawContent.isEmpty()) {
			segments.addAll(parseTextContent(rawContent, message));
		}

		// Attachments (images, files, audio, etc.)
		if (isEnabled("attachments")) {
			for (Message.Attachment attachment : message.getAttachments()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String type = attachment.isImage() ? "image"
						: attachment.isVideo() ? "video"
						: "file";
				String display = "<attachment type=\"" + type + "\" name=\"" + attachment.getFileName() + "\">";
				segments.add(new TextSegment(display, LINK_COLOR)
						.withUnderlined(true)
						.withClickUrl(attachment.getUrl())
						.withHoverText(attachment.getFileName() + " (" + formatFileSize(attachment.getSize()) + ") - Click to open"));
			}
		}

		// Embeds (rich embeds, link previews, bot structured messages)
		if (isEnabled("embeds")) {
			for (MessageEmbed embed : message.getEmbeds()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				String title = embed.getTitle();
				if (title == null || title.isEmpty()) {
					title = embed.getDescription() != null ? truncate(embed.getDescription(), 30) : "embed";
				}
				segments.add(new TextSegment("<embed title=\"" + title + "\">", YELLOW));
			}
		}

		// Stickers
		if (isEnabled("stickers")) {
			for (StickerItem sticker : message.getStickers()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				segments.add(new TextSegment("<sticker name=\"" + sticker.getName() + "\">", YELLOW));
			}
		}

		// Interactive components (buttons, select menus, action rows)
		if (isEnabled("components")) {
			if (!message.getComponents().isEmpty()) {
				if (!segments.isEmpty()) {
					segments.add(new TextSegment(" "));
				}
				segments.add(new TextSegment("<interactive components>", YELLOW));
			}
		}

		return segments;
	}

	/**
	 * Parses the raw text content of a Discord message into text segments.
	 * Handles mentions, custom emojis, Unicode emojis, markdown, and hyperlinks.
	 *
	 * @param rawContent The raw content string from {@link Message#getContentRaw()}.
	 * @param message    The original message for resolving mentions.
	 * @return A list of TextSegments for the text content.
	 */
	private static List<TextSegment> parseTextContent(String rawContent, Message message) {
		List<TextSegment> segments = new ArrayList<>();

		// Build a combined pattern for all replaceable tokens
		// Order matters: user mentions, role mentions, channel mentions, custom emojis, URLs
		StringBuilder combinedRegex = new StringBuilder();
		List<String> activeGroups = new ArrayList<>();

		if (isEnabled("user_mentions")) {
			appendGroup(combinedRegex, USER_MENTION_PATTERN.pattern(), "userMention");
			activeGroups.add("userMention");
		}
		if (isEnabled("role_mentions")) {
			appendGroup(combinedRegex, ROLE_MENTION_PATTERN.pattern(), "roleMention");
			activeGroups.add("roleMention");
		}
		if (isEnabled("channel_mentions")) {
			appendGroup(combinedRegex, CHANNEL_MENTION_PATTERN.pattern(), "channelMention");
			activeGroups.add("channelMention");
		}
		if (isEnabled("custom_emojis")) {
			appendGroup(combinedRegex, CUSTOM_EMOJI_PATTERN.pattern(), "customEmoji");
			activeGroups.add("customEmoji");
		}
		if (isEnabled("hyperlinks")) {
			appendGroup(combinedRegex, URL_PATTERN.pattern(), "hyperlink");
			activeGroups.add("hyperlink");
		}

		if (combinedRegex.isEmpty()) {
			// No special parsing enabled, just return plain text with optional markdown
			segments.addAll(parseMarkdownOrPlain(rawContent));
			return segments;
		}

		Pattern combined = Pattern.compile(combinedRegex.toString());
		Matcher matcher = combined.matcher(rawContent);

		int lastEnd = 0;
		while (matcher.find()) {
			// Add plain text before this match
			if (matcher.start() > lastEnd) {
				String plainText = rawContent.substring(lastEnd, matcher.start());
				segments.addAll(parseMarkdownOrPlain(plainText));
			}

			// Determine which group matched
			if (activeGroups.contains("userMention") && matcher.group("userMention") != null) {
				String userId = extractFirstDigits(matcher.group("userMention"));
				segments.add(resolveUserMention(userId, message));
			} else if (activeGroups.contains("roleMention") && matcher.group("roleMention") != null) {
				String roleId = extractFirstDigits(matcher.group("roleMention"));
				segments.add(resolveRoleMention(roleId, message));
			} else if (activeGroups.contains("channelMention") && matcher.group("channelMention") != null) {
				String channelId = extractFirstDigits(matcher.group("channelMention"));
				segments.add(resolveChannelMention(channelId, message));
			} else if (activeGroups.contains("customEmoji") && matcher.group("customEmoji") != null) {
				String emojiName = extractCustomEmojiName(matcher.group("customEmoji"));
				segments.add(new TextSegment(":" + emojiName + ":", YELLOW));
			} else if (activeGroups.contains("hyperlink") && matcher.group("hyperlink") != null) {
				String url = matcher.group("hyperlink");
				segments.add(new TextSegment(url, LINK_COLOR)
						.withUnderlined(true)
						.withClickUrl(url)
						.withHoverText("Click to open link"));
			}

			lastEnd = matcher.end();
		}

		// Add remaining plain text
		if (lastEnd < rawContent.length()) {
			String remaining = rawContent.substring(lastEnd);
			segments.addAll(parseMarkdownOrPlain(remaining));
		}

		return segments;
	}

	/**
	 * Parses a plain text fragment, optionally applying markdown formatting and Unicode emoji conversion.
	 *
	 * @param text The plain text to parse.
	 * @return A list of TextSegments.
	 */
	private static List<TextSegment> parseMarkdownOrPlain(String text) {
		List<TextSegment> segments = new ArrayList<>();

		if (isEnabled("unicode_emojis")) {
			// Split text around Unicode emojis
			segments.addAll(parseUnicodeEmojis(text));
		} else if (isEnabled("markdown")) {
			segments.addAll(parseMarkdown(text));
		} else {
			if (!text.isEmpty()) {
				segments.add(new TextSegment(text));
			}
		}

		return segments;
	}

	/**
	 * Splits text around Unicode emoji characters, converting them to {@code :name:} format.
	 *
	 * @param text The text possibly containing Unicode emojis.
	 * @return A list of TextSegments with emojis rendered as {@code :name:} in yellow.
	 */
	private static List<TextSegment> parseUnicodeEmojis(String text) {
		List<TextSegment> segments = new ArrayList<>();

		// Use JEmoji to extract all emoji occurrences with their positions
		var emojiResults = EmojiManager.extractEmojisInOrderWithIndex(text);

		if (emojiResults.isEmpty()) {
			// No emojis found, apply markdown or return plain text
			if (isEnabled("markdown")) {
				segments.addAll(parseMarkdown(text));
			} else if (!text.isEmpty()) {
				segments.add(new TextSegment(text));
			}
			return segments;
		}

		int lastEnd = 0;
		for (var result : emojiResults) {
			int start = result.getCharIndex();
			int end = result.getEndCharIndex();

			// Add text before this emoji
			if (start > lastEnd) {
				String before = text.substring(lastEnd, start);
				if (isEnabled("markdown")) {
					segments.addAll(parseMarkdown(before));
				} else if (!before.isEmpty()) {
					segments.add(new TextSegment(before));
				}
			}

			// Convert emoji to :name: format, preferring Discord aliases
			var emoji = result.getEmoji();
			String shortcode = getEmojiShortcode(emoji);
			// Ensure the shortcode has colons
			if (!shortcode.startsWith(":")) {
				shortcode = ":" + shortcode + ":";
			}
			segments.add(new TextSegment(shortcode, YELLOW));

			lastEnd = end;
		}

		// Add remaining text after last emoji
		if (lastEnd < text.length()) {
			String remaining = text.substring(lastEnd);
			if (isEnabled("markdown")) {
				segments.addAll(parseMarkdown(remaining));
			} else if (!remaining.isEmpty()) {
				segments.add(new TextSegment(remaining));
			}
		}

		return segments;
	}

	/**
	 * Gets a human-readable shortcode for a Unicode emoji.
	 * Prefers Discord aliases (e.g. {@code :blush:}), falls back to general aliases,
	 * and finally to the raw Unicode character if no alias is available.
	 *
	 * @param emoji The JEmoji Emoji object.
	 * @return The shortcode string (with or without colons).
	 */
	private static String getEmojiShortcode(net.fellbaum.jemoji.Emoji emoji) {
		if (!emoji.getDiscordAliases().isEmpty()) {
			return emoji.getDiscordAliases().getFirst();
		}
		if (!emoji.getAllAliases().isEmpty()) {
			return emoji.getAllAliases().getFirst();
		}
		return emoji.getEmoji();
	}

	/**
	 * Parses Discord markdown in text and returns styled segments.
	 * Handles bold, italic, underline, strikethrough, spoiler, code blocks, and inline code.
	 *
	 * @param text The text with Discord markdown.
	 * @return A list of styled TextSegments.
	 */
	private static List<TextSegment> parseMarkdown(String text) {
		List<TextSegment> segments = new ArrayList<>();

		if (text == null || text.isEmpty()) {
			return segments;
		}

		// Process markdown patterns in priority order
		// Code blocks and inline code first (they prevent other formatting)
		Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
		if (codeBlockMatcher.find()) {
			addBeforeMatch(segments, text, codeBlockMatcher.start());
			segments.add(new TextSegment("[" + codeBlockMatcher.group(1).trim() + "]", "dark_gray"));
			addAfterMatch(segments, text, codeBlockMatcher.end());
			return segments;
		}

		Matcher inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(text);
		if (inlineCodeMatcher.find()) {
			addBeforeMatch(segments, text, inlineCodeMatcher.start());
			segments.add(new TextSegment(inlineCodeMatcher.group(1), "dark_gray"));
			addAfterMatch(segments, text, inlineCodeMatcher.end());
			return segments;
		}

		// Spoiler
		Matcher spoilerMatcher = SPOILER_PATTERN.matcher(text);
		if (spoilerMatcher.find()) {
			addBeforeMatch(segments, text, spoilerMatcher.start());
			segments.add(new TextSegment("[spoiler]", "dark_gray")
					.withHoverText(spoilerMatcher.group(1)));
			addAfterMatch(segments, text, spoilerMatcher.end());
			return segments;
		}

		// Bold
		Matcher boldMatcher = BOLD_PATTERN.matcher(text);
		if (boldMatcher.find()) {
			addBeforeMatch(segments, text, boldMatcher.start());
			segments.add(new TextSegment(boldMatcher.group(1)).withBold(true));
			addAfterMatch(segments, text, boldMatcher.end());
			return segments;
		}

		// Underline (must come before italic since __ could conflict with _)
		Matcher underlineMatcher = UNDERLINE_PATTERN.matcher(text);
		if (underlineMatcher.find()) {
			addBeforeMatch(segments, text, underlineMatcher.start());
			segments.add(new TextSegment(underlineMatcher.group(1)).withUnderlined(true));
			addAfterMatch(segments, text, underlineMatcher.end());
			return segments;
		}

		// Strikethrough
		Matcher strikethroughMatcher = STRIKETHROUGH_PATTERN.matcher(text);
		if (strikethroughMatcher.find()) {
			addBeforeMatch(segments, text, strikethroughMatcher.start());
			segments.add(new TextSegment(strikethroughMatcher.group(1)).withStrikethrough(true));
			addAfterMatch(segments, text, strikethroughMatcher.end());
			return segments;
		}

		// Italic (underscore)
		Matcher italicUnderscoreMatcher = ITALIC_UNDERSCORE_PATTERN.matcher(text);
		if (italicUnderscoreMatcher.find()) {
			addBeforeMatch(segments, text, italicUnderscoreMatcher.start());
			segments.add(new TextSegment(italicUnderscoreMatcher.group(1)).withItalic(true));
			addAfterMatch(segments, text, italicUnderscoreMatcher.end());
			return segments;
		}

		// Italic (asterisk)
		Matcher italicAsteriskMatcher = ITALIC_ASTERISK_PATTERN.matcher(text);
		if (italicAsteriskMatcher.find()) {
			addBeforeMatch(segments, text, italicAsteriskMatcher.start());
			segments.add(new TextSegment(italicAsteriskMatcher.group(1)).withItalic(true));
			addAfterMatch(segments, text, italicAsteriskMatcher.end());
			return segments;
		}

		// No markdown found, return as plain text
		if (!text.isEmpty()) {
			segments.add(new TextSegment(text));
		}

		return segments;
	}

	/**
	 * Adds segments for text before a markdown match.
	 */
	private static void addBeforeMatch(List<TextSegment> segments, String text, int matchStart) {
		if (matchStart > 0) {
			String before = text.substring(0, matchStart);
			if (!before.isEmpty()) {
				segments.add(new TextSegment(before));
			}
		}
	}

	/**
	 * Recursively adds segments for text after a markdown match.
	 */
	private static void addAfterMatch(List<TextSegment> segments, String text, int matchEnd) {
		if (matchEnd < text.length()) {
			String after = text.substring(matchEnd);
			segments.addAll(parseMarkdown(after));
		}
	}

	/**
	 * Resolves a user mention ID to a styled TextSegment.
	 *
	 * @param userId  The Discord user ID string.
	 * @param message The original message for resolving mentions.
	 * @return A TextSegment with the user's display name and role color.
	 */
	private static TextSegment resolveUserMention(String userId, Message message) {
		for (User user : message.getMentions().getUsers()) {
			if (user.getId().equals(userId)) {
				Member member = message.getGuild().getMemberById(userId);
				String displayName = member != null ? member.getEffectiveName() : user.getName();
				String color = member != null ? getMemberRoleColor(member) : null;
				return new TextSegment("[@" + displayName + "]", color);
			}
		}
		// Fallback: unresolved user mention
		return new TextSegment("[@" + userId + "]");
	}

	/**
	 * Resolves a role mention ID to a styled TextSegment.
	 *
	 * @param roleId  The Discord role ID string.
	 * @param message The original message for resolving mentions.
	 * @return A TextSegment with the role name and role color.
	 */
	private static TextSegment resolveRoleMention(String roleId, Message message) {
		for (Role role : message.getMentions().getRoles()) {
			if (role.getId().equals(roleId)) {
				String color = role.getColor() != null
						? String.format("#%06X", role.getColor().getRGB() & 0xFFFFFF)
						: null;
				return new TextSegment("[@" + role.getName() + "]", color);
			}
		}
		// Fallback: unresolved role mention
		return new TextSegment("[@" + roleId + "]");
	}

	/**
	 * Resolves a channel mention ID to a styled TextSegment.
	 *
	 * @param channelId The Discord channel ID string.
	 * @param message   The original message for resolving mentions.
	 * @return A TextSegment with the channel name in yellow.
	 */
	private static TextSegment resolveChannelMention(String channelId, Message message) {
		for (GuildChannel channel : message.getMentions().getChannels()) {
			if (channel.getId().equals(channelId)) {
				return new TextSegment("[#" + channel.getName() + "]", YELLOW);
			}
		}
		// Fallback: unresolved channel mention
		return new TextSegment("[#" + channelId + "]", YELLOW);
	}

	/**
	 * Gets the hex color string of a member's top colored role.
	 *
	 * @param member The Discord guild member.
	 * @return The hex color string (e.g. "#FF0000"), or null if no colored role.
	 */
	public static String getMemberRoleColor(Member member) {
		if (member == null) return null;
		java.awt.Color color = member.getColor();
		if (color == null) return null;
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/**
	 * Checks if a specific message parsing feature is enabled in config.
	 *
	 * @param key The feature key (e.g. "attachments", "user_mentions").
	 * @return true if the feature is enabled.
	 */
	private static boolean isEnabled(String key) {
		return ConfigManager.getBoolean("message_parsing.discord_to_minecraft." + key, true);
	}

	/**
	 * Appends a named group to the combined regex pattern.
	 *
	 * @param sb      The StringBuilder for the combined regex.
	 * @param pattern The regex pattern to add.
	 * @param name    The named group name.
	 */
	private static void appendGroup(StringBuilder sb, String pattern, String name) {
		if (!sb.isEmpty()) {
			sb.append("|");
		}
		sb.append("(?<").append(name).append(">").append(pattern).append(")");
	}

	/**
	 * Extracts the first sequence of digits from a string.
	 *
	 * @param text The text to extract digits from.
	 * @return The first digit sequence, or the original text if no digits found.
	 */
	private static String extractFirstDigits(String text) {
		Matcher m = Pattern.compile("(\\d+)").matcher(text);
		return m.find() ? m.group(1) : text;
	}

	/**
	 * Extracts the emoji name from a custom emoji match string like {@code <:name:123>} or {@code <a:name:123>}.
	 *
	 * @param emojiRaw The raw custom emoji string.
	 * @return The emoji name.
	 */
	private static String extractCustomEmojiName(String emojiRaw) {
		Matcher m = CUSTOM_EMOJI_PATTERN.matcher(emojiRaw);
		return m.find() ? m.group(1) : emojiRaw;
	}

	/**
	 * Formats a file size in bytes into a human-readable string.
	 *
	 * @param bytes The file size in bytes.
	 * @return A human-readable file size (e.g. "1.2 MB").
	 */
	private static String formatFileSize(int bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}

	/**
	 * Truncates a string to a maximum length, adding "..." if truncated.
	 *
	 * @param text      The text to truncate.
	 * @param maxLength The maximum length.
	 * @return The truncated text.
	 */
	private static String truncate(String text, int maxLength) {
		if (text.length() <= maxLength) return text;
		return text.substring(0, maxLength) + "...";
	}
}
