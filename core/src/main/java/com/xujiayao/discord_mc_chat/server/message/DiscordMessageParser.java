package com.xujiayao.discord_mc_chat.server.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.fellbaum.jemoji.EmojiManager;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

	// Discord Markdown patterns
	private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n?([\\s\\S]*?)```");
	private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6}\\s+.*)$");

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

	// Unicode emoji pattern (basic, covering common emoji ranges)
	private static final Pattern UNICODE_EMOJI_PATTERN = Pattern.compile(
			"[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
					"[\\x{1F1E0}-\\x{1F1FF}]|[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}]|" +
					"[\\x{FE00}-\\x{FE0F}]|[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA00}-\\x{1FA6F}]|" +
					"[\\x{1FA70}-\\x{1FAFF}]|\\x{200D}|\\x{20E3}|" +
					"[\\x{231A}-\\x{231B}]|[\\x{23E9}-\\x{23F3}]|[\\x{23F8}-\\x{23FA}]|" +
					"[\\x{25AA}-\\x{25AB}]|\\x{25B6}|\\x{25C0}|[\\x{25FB}-\\x{25FE}]|" +
					"[\\x{2614}-\\x{2615}]|[\\x{2648}-\\x{2653}]|\\x{267F}|\\x{2693}|" +
					"\\x{26A1}|[\\x{26AA}-\\x{26AB}]|[\\x{26BD}-\\x{26BE}]|" +
					"[\\x{26C4}-\\x{26C5}]|\\x{26CE}|\\x{26D4}|\\x{26EA}|" +
					"[\\x{26F2}-\\x{26F3}]|\\x{26F5}|\\x{26FA}|\\x{26FD}|" +
					"\\x{2702}|\\x{2705}|[\\x{2708}-\\x{270D}]|\\x{270F}"
	);

	// ANSI escape sequence pattern for ```ansi code blocks
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\x1B\\[(\\d+(?:;\\d+)*)m");

	// Hyperlink pattern: [text](url) or bare URLs (excluding trailing Markdown delimiters)
	private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
	// Matches spoiler-wrapped user mentions: ||<@123>|| / ||<@!123>||
	private static final Pattern SPOILER_USER_MENTION_PATTERN = Pattern.compile("\\|\\|<@!?(\\d+)>\\|\\|");
	// Matches spoiler-wrapped role mentions: ||<@&123>||
	private static final Pattern SPOILER_ROLE_MENTION_PATTERN = Pattern.compile("\\|\\|<@&(\\d+)>\\|\\|");
	// Matches spoiler-wrapped channel mentions: ||<#123>||
	private static final Pattern SPOILER_CHANNEL_MENTION_PATTERN = Pattern.compile("\\|\\|<#(\\d+)>\\|\\|");
	// Matches spoiler-wrapped @everyone/@here tokens: ||@everyone|| / ||@here||
	private static final Pattern SPOILER_EVERYONE_HERE_PATTERN = Pattern.compile("\\|\\|@(everyone|here)\\|\\|");
	private static final Pattern SPOILER_CONTENT_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
	private static final Pattern LINK_TOKEN_PATTERN = Pattern.compile("\\[([^]]+)]\\((https?://[^)]+)\\)");
	private static final List<String> MARKDOWN_DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");

	private static final int MAX_CONTENT_LINES = 6;
	private static final int REPLY_TRUNCATE_LIMIT_WIDE = 20;
	private static final int REPLY_TRUNCATE_LIMIT_NARROW = 40;
	private static final int MAIN_TRUNCATE_LIMIT_WIDE = 200;
	private static final int MAIN_TRUNCATE_LIMIT_NARROW = 400;
	private static final String URL_COLOR = "#3366CC";
	private static final String ATTACHMENT_LABEL_PREFIX = "<attachment type=[%s] name=[";
	private static final String EMBED_LABEL_PREFIX = "<embed title=[";
	private static final String LABEL_SUFFIX = "]>";

	/**
	 * Builds the main message line segments for a Discord chat message.
	 * <p>
	 * The format follows the custom_messages {@code common.chat} pattern:
	 * [server] &lt;effective_name&gt; {parsed message content}
	 * <p>
	 * For multi-line messages, uses YAML-style format:
	 * [server] &lt;effective_name&gt; |
	 * Line 1
	 * Line 2
	 * ...
	 *
	 * @param message The Discord message.
	 * @return The list of text segments for the main message line.
	 */
	public static List<TextSegment> buildChatSegments(Message message) {
		List<TextSegment> segments = new ArrayList<>();

		Member member = message.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = getRoleColorHex(member);

		String raw = message.getContentRaw();
		String truncatedRaw = truncateMainRaw(raw);
		boolean isMultiLine = truncatedRaw.contains("\n");

		// Build segments from xxxxx_to_minecraft.user_message template
		JsonNode chatNode = I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("user_message");
		if (chatNode.isArray()) {
			for (JsonNode segNode : chatNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				// Replace placeholders
				text = replacePlaceholders(text, effectiveName, roleColor);
				color = replacePlaceholders(color, effectiveName, roleColor);

				if (text.contains("{message}")) {
					// Split around {message} and inject parsed message content
					String[] parts = text.split("\\{message}", -1);
					if (!parts[0].isEmpty()) {
						segments.add(new TextSegment(parts[0], bold, color));
					}

					if (isMultiLine) {
						// YAML-style multi-line: append "|" then newline-separated content
						segments.add(new TextSegment("|", bold, color));

						// Parse already-truncated content
						List<TextSegment> contentSegments = parseMessageContent(message, truncatedRaw);

						// Apply default color inheritance
						applyDefaultColor(contentSegments, color);

						// Prepend newline to first content segment
						if (!contentSegments.isEmpty()) {
							contentSegments.getFirst().text = "\n" + contentSegments.getFirst().text;
						}
						segments.addAll(contentSegments);
					} else {
						List<TextSegment> contentSegments = parseMessageContent(message, truncatedRaw);

						// Apply default color inheritance
						applyDefaultColor(contentSegments, color);

						segments.addAll(contentSegments);
					}

					if (parts.length > 1 && !parts[1].isEmpty()) {
						segments.add(new TextSegment(parts[1], bold, color));
					}
				} else {
					segments.add(new TextSegment(text, bold, color));
				}
			}
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

		JsonNode commandNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("command");
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
	 * The referenced message content is parsed through the same pipeline as the main message,
	 * but truncated to a single line (at first newline or at width-based character limit).
	 *
	 * @param referencedMessage The message being replied to.
	 * @return The list of text segments for the reply line, or null if no reply.
	 */
	public static List<TextSegment> buildReplySegments(Message referencedMessage) {
		if (referencedMessage == null) {
			return null;
		}
		Member refMember = referencedMessage.getMember();
		String refName = refMember != null ? refMember.getEffectiveName() : referencedMessage.getAuthor().getName();
		String refRoleColor = getRoleColorHex(refMember);
		return buildReplySegments(refName, refRoleColor, referencedMessage, referencedMessage.getContentRaw());
	}

	/**
	 * Builds reply context line segments from cached/reference fields.
	 *
	 * @param refName        referenced message author's display name
	 * @param refRoleColor   referenced message author's role color
	 * @param contextMessage message context for full parsing; may be null for cached/deleted messages
	 * @param refRaw         raw referenced message content
	 * @return reply line segments, or null when refRaw is null
	 */
	public static List<TextSegment> buildReplySegments(String refName, String refRoleColor, Message contextMessage, String refRaw) {
		if (refRaw == null) {
			return null;
		}
		List<TextSegment> segments = new ArrayList<>();
		String truncatedRaw = truncateReplyRaw(refRaw);
		List<TextSegment> refContentSegments = contextMessage != null
				? parseMessageContent(contextMessage, truncatedRaw)
				: parseMessageContentWithoutMessage(truncatedRaw);
		refContentSegments = enforceSingleLine(refContentSegments);

		JsonNode responseNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("response");
		if (responseNode.isArray()) {
			for (JsonNode segNode : responseNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", refName);
				color = color.replace("{role_color}", refRoleColor);

				if (text.contains("{message}")) {
					String[] parts = text.split("\\{message}", -1);
					if (!parts[0].isEmpty()) {
						segments.add(new TextSegment(parts[0], bold, color));
					}
					applyDefaultColor(refContentSegments, color);
					segments.addAll(refContentSegments);
					if (parts.length > 1 && !parts[1].isEmpty()) {
						segments.add(new TextSegment(parts[1], bold, color));
					}
				} else {
					segments.add(new TextSegment(text, bold, color));
				}
			}
		}

		return segments;
	}

	/**
	 * Builds segments for a reaction event.
	 * <p>
	 * Format follows the custom_messages {@code discord_to_minecraft.reaction} pattern.
	 *
	 * @param reactorName The display name of the user who reacted.
	 * @param roleColor   The hex color of the reactor's highest role.
	 * @param emojiText   The emoji display text (e.g. ":test:").
	 * @return The list of text segments for the reaction notification.
	 */
	public static List<TextSegment> buildReactionSegments(String reactorName, String roleColor, String emojiText) {
		List<TextSegment> segments = new ArrayList<>();

		JsonNode reactionNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("reaction");
		if (reactionNode.isArray()) {
			for (JsonNode segNode : reactionNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", reactorName)
						.replace("{emoji}", emojiText);
				color = color.replace("{role_color}", roleColor);

				segments.add(new TextSegment(text, bold, color));
			}
		}

		return segments;
	}

	/**
	 * Builds segments for a message edit notification line.
	 * <p>
	 * Format follows the custom_messages {@code discord_to_minecraft.edit} pattern.
	 *
	 * @param editorName The display name of the user who edited.
	 * @param roleColor  The hex color of the editor's highest role.
	 * @return The list of text segments for the edit notification.
	 */
	public static List<TextSegment> buildEditNotificationSegments(String editorName, String roleColor) {
		List<TextSegment> segments = new ArrayList<>();

		JsonNode editNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("edit");
		if (editNode.isArray()) {
			for (JsonNode segNode : editNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", editorName);
				color = color.replace("{role_color}", roleColor);

				segments.add(new TextSegment(text, bold, color));
			}
		}

		return segments;
	}

	/**
	 * Builds segments for the edited message content line shown after edit notification.
	 * <p>
	 * Format follows the custom_messages {@code discord_to_minecraft.edited_message} pattern.
	 * This is intentionally separated from {@code common.chat} so edit events can render a
	 * "bottom bun" style complementary to {@code discord_to_minecraft.response}.
	 *
	 * @param message The edited Discord message.
	 * @return The list of text segments for the edited message content line.
	 */
	public static List<TextSegment> buildEditedMessageSegments(Message message) {
		List<TextSegment> segments = new ArrayList<>();
		Member member = message.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = getRoleColorHex(member);
		String truncatedRaw = truncateMainRaw(message.getContentRaw());
		List<TextSegment> contentSegments = parseMessageContent(message, truncatedRaw);

		JsonNode editedNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("edited_message");
		if (editedNode.isArray()) {
			for (JsonNode segNode : editedNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", effectiveName);
				color = color.replace("{role_color}", roleColor);

				if (text.contains("{message}")) {
					String[] parts = text.split("\\{message}", -1);
					if (!parts[0].isEmpty()) {
						segments.add(new TextSegment(parts[0], bold, color));
					}
					applyDefaultColor(contentSegments, color);
					segments.addAll(contentSegments);
					if (parts.length > 1 && !parts[1].isEmpty()) {
						segments.add(new TextSegment(parts[1], bold, color));
					}
				} else {
					segments.add(new TextSegment(text, bold, color));
				}
			}
		}

		return segments;
	}

	/**
	 * Builds segments for a message delete notification.
	 * <p>
	 * Format follows the custom_messages {@code discord_to_minecraft.delete} pattern.
	 *
	 * @param deleterName The display name of the user who deleted.
	 * @param roleColor   The hex color of the deleter's highest role.
	 * @return The list of text segments for the delete notification.
	 */
	public static List<TextSegment> buildDeleteSegments(String deleterName, String roleColor) {
		List<TextSegment> segments = new ArrayList<>();

		JsonNode deleteNode = I18nManager.getCustomMessages().path("discord_to_minecraft").path("delete");
		if (deleteNode.isArray()) {
			for (JsonNode segNode : deleteNode) {
				String text = segNode.path("text").asText("");
				boolean bold = segNode.path("bold").asBoolean(false);
				String color = segNode.path("color").asText("");

				text = text.replace("{effective_name}", deleterName);
				color = color.replace("{role_color}", roleColor);

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
		String template = I18nManager.getCustomMessages().path("discord_to_minecraft").path("mentioned").asText();
		return template.replace("{effective_name}", effectiveName);
	}

	/**
	 * Checks whether the message contains @everyone or @here mentions.
	 *
	 * @param message The Discord message.
	 * @return true if the message mentions everyone/here.
	 */
	public static boolean isMentionEveryone(Message message) {
		return message.getMentions().mentionsEveryone();
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

	/**
	 * Parses the content of a Discord message into a list of styled text segments,
	 * using the provided raw content string instead of the message's own raw content.
	 * <p>
	 * This overload is used for reply truncation and multi-line limiting.
	 *
	 * @param message The Discord message (for resolving mentions, attachments, etc.).
	 * @param raw     The raw content string to parse (may be truncated).
	 * @return The list of text segments representing the parsed message body.
	 */
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
						title = safeTruncate(title, 20) + "...";
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

	/**
	 * Parses the raw text content of a Discord message, handling mentions, emojis,
	 * Markdown, hyperlinks, timestamps, and @everyone/@here inline.
	 */
	private static List<TextSegment> parseRawContent(String raw, Message message,
													 boolean parseMentions, boolean parseCustomEmojis,
													 boolean parseUnicodeEmojis, boolean parseMarkdown,
													 boolean parseHyperlinks, boolean parseTimestamps) {
		List<TextSegment> segments = new ArrayList<>();

		List<TokenSpan> tokens = new ArrayList<>();

		if (parseMentions) {
			collectSpoilerMentionTokens(raw, message, tokens);
			collectUserMentionTokens(raw, message, tokens);
			collectRoleMentionTokens(raw, message, tokens);
			collectChannelMentionTokens(raw, message, tokens);
			collectEveryoneHereTokens(raw, message, tokens);
		}

		// Collect timestamps if configured
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

		return postProcessInlineSegments(segments, parseCustomEmojis, parseUnicodeEmojis, parseHyperlinks);
	}

	private static List<TextSegment> parseMessageContentWithoutMessage(String raw) {
		boolean parseCustomEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.custom_emojis");
		boolean parseUnicodeEmojis = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.unicode_emojis");
		boolean parseMarkdown = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown");
		boolean parseHyperlinks = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.hyperlinks");
		boolean parseTimestamps = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.timestamps");
		return parseRawContent(raw, null, false, parseCustomEmojis, parseUnicodeEmojis, parseMarkdown, parseHyperlinks, parseTimestamps);
	}

	private static void collectUserMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = USER_MENTION_PATTERN.matcher(raw);
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
			TextSegment seg = new TextSegment("[@" + displayName + "]", false, color != null ? color : "white");
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectRoleMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = ROLE_MENTION_PATTERN.matcher(raw);
		while (matcher.find()) {
			String roleId = matcher.group(1);
			String roleName = roleId;
			String color = "white";
			for (Role role : message.getMentions().getRoles()) {
				if (role.getId().equals(roleId)) {
					roleName = role.getName();
					Color roleColor = role.getColors().getPrimary();
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
	 * Collects @everyone and @here mention tokens from the raw content.
	 * Only active when the message actually has the mentionsEveryone flag set.
	 */
	private static void collectEveryoneHereTokens(String raw, Message message, List<TokenSpan> tokens) {
		if (!message.getMentions().mentionsEveryone()) {
			return;
		}
		Matcher matcher = EVERYONE_HERE_PATTERN.matcher(raw);
		while (matcher.find()) {
			String mention = matcher.group(1); // "everyone" or "here"
			TextSegment seg = new TextSegment("[@" + mention + "]", false, "yellow");
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectSpoilerMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		collectSpoilerUserMentionTokens(raw, message, tokens);
		collectSpoilerRoleMentionTokens(raw, message, tokens);
		collectSpoilerChannelMentionTokens(raw, message, tokens);
		collectSpoilerEveryoneHereTokens(raw, message, tokens);
	}

	private static void collectSpoilerUserMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = SPOILER_USER_MENTION_PATTERN.matcher(raw);
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
			applySpoilerStyle(seg);
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectSpoilerRoleMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = SPOILER_ROLE_MENTION_PATTERN.matcher(raw);
		while (matcher.find()) {
			String roleId = matcher.group(1);
			String roleName = roleId;
			String color = "white";
			for (Role role : message.getMentions().getRoles()) {
				if (role.getId().equals(roleId)) {
					roleName = role.getName();
					Color roleColor = role.getColors().getPrimary();
					if (roleColor != null) {
						color = String.format("#%06X", roleColor.getRGB() & 0xFFFFFF);
					}
					break;
				}
			}
			TextSegment seg = new TextSegment("[@" + roleName + "]", false, color);
			applySpoilerStyle(seg);
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectSpoilerChannelMentionTokens(String raw, Message message, List<TokenSpan> tokens) {
		Matcher matcher = SPOILER_CHANNEL_MENTION_PATTERN.matcher(raw);
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
			applySpoilerStyle(seg);
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	private static void collectSpoilerEveryoneHereTokens(String raw, Message message, List<TokenSpan> tokens) {
		if (!message.getMentions().mentionsEveryone()) {
			return;
		}
		Matcher matcher = SPOILER_EVERYONE_HERE_PATTERN.matcher(raw);
		while (matcher.find()) {
			String mention = matcher.group(1);
			TextSegment seg = new TextSegment("[@" + mention + "]", false, "yellow");
			applySpoilerStyle(seg);
			tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
		}
	}

	/**
	 * Collects Discord timestamp tokens from the raw content.
	 * Timestamps like {@code <t:1234567890:f>} are resolved using the server's locale.
	 */
	private static void collectTimestampTokens(String raw, List<TokenSpan> tokens) {
		Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(raw);
		while (matcher.find()) {
			try {
				long epoch = Long.parseLong(matcher.group(1));
				String style = matcher.group(2);
				String formatted = formatDiscordTimestamp(epoch, style);
				TextSegment seg = new TextSegment("[" + formatted + "]", false, "yellow");
				tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
			} catch (Exception ignored) {
				// If parsing fails, leave the raw token as-is
			}
		}
	}

	private static String formatDiscordTimestamp(long epoch, String style) {
		Instant instant = Instant.ofEpochSecond(epoch);
		Locale locale = getDmccLocale();
		ZoneId zone = ZoneId.systemDefault();

		if (style == null) {
			style = "f"; // Discord default
		}

		return switch (style) {
			case "t" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "T" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
					.format(instant.atZone(zone));
			case "d" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "D" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
					.format(instant.atZone(zone));
			case "s" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "S" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).withLocale(locale)
					.format(instant.atZone(zone));
			case "F" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
			case "R" -> {
				long now = Instant.now().getEpochSecond();
				long diff = now - epoch;
				yield formatRelativeTime(diff);
			}
			default -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
					.format(instant.atZone(zone));
		};
	}

	private static String formatRelativeTime(long diffSeconds) {
		boolean past = diffSeconds >= 0;
		long abs = Math.abs(diffSeconds);

		String unitKey;
		long value;
		if (abs < 60) {
			value = abs;
			unitKey = "second";
		} else if (abs < 3600) {
			value = abs / 60;
			unitKey = "minute";
		} else if (abs < 86400) {
			value = abs / 3600;
			unitKey = "hour";
		} else if (abs < 2592000) {
			value = abs / 86400;
			unitKey = "day";
		} else if (abs < 31536000) {
			value = abs / 2592000;
			unitKey = "month";
		} else {
			value = abs / 31536000;
			unitKey = "year";
		}

		String unitTranslationKey = String.format(
				"discord.message_parser.relative.units.%s.%s",
				unitKey,
				value == 1 ? "one" : "other"
		);
		String unit = I18nManager.getDmccTranslation(unitTranslationKey);
		return past
				? I18nManager.getDmccTranslation("discord.message_parser.relative.past", value, unit)
				: I18nManager.getDmccTranslation("discord.message_parser.relative.future", value, unit);
	}

	private static Locale getDmccLocale() {
		String languageCode = I18nManager.getLanguage();
		if (languageCode == null || languageCode.isBlank()) {
			return Locale.ENGLISH;
		}
		// DMCC language codes are configured as snake_case (e.g. en_us, zh_cn),
		// while Locale.forLanguageTag expects BCP-47 with hyphen separators.
		String tag = languageCode.replace('_', '-');
		Locale locale = Locale.forLanguageTag(tag);
		if (locale.getLanguage().isBlank()) {
			return Locale.ENGLISH;
		}
		return locale;
	}

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
	 * Parses simple Markdown formatting in plain text into styled segments.
	 * <p>
	 * Handles: bold-italic (***), bold (**), italic (* or _), underline (__),
	 * strikethrough (~~), spoiler (||), code blocks (```), inline code (`),
	 * and headings (# at start of line).
	 */
	private static List<TextSegment> parseMarkdownText(String text) {
		List<TextSegment> segments = new ArrayList<>();

		List<MarkdownSpan> spans = new ArrayList<>();
		collectCodeBlockSpans(text, spans);
		spans.sort(Comparator.comparingInt(a -> a.start));
		spans = removeMarkdownOverlaps(spans);

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
			if (HEADING_PATTERN.matcher(line).matches()) {
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
				if (isUnderscoreDelimiter(delimiter) && isInsideDiscordAliasEmoji(text, index)) {
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
			if (isUnderscoreDelimiter(delimiter) && isInsideDiscordAliasEmoji(text, i)) {
				continue;
			}
			if (text.startsWith(delimiter, i)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isUnderscoreDelimiter(String delimiter) {
		return "_".equals(delimiter) || "__".equals(delimiter);
	}

	private static boolean isInsideDiscordAliasEmoji(String text, int index) {
		if (index <= 0 || index >= text.length()) {
			return false;
		}
		int leftColon = text.lastIndexOf(':', index);
		if (leftColon < 0) {
			return false;
		}
		int rightColon = text.indexOf(':', index);
		if (rightColon < 0 || rightColon <= leftColon + 1) {
			return false;
		}
		if (index <= leftColon || index >= rightColon) {
			return false;
		}
		String candidate = text.substring(leftColon, rightColon + 1);
		return DISCORD_ALIAS_EMOJI_PATTERN.matcher(candidate).matches();
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
		if (segment.obfuscated) {
			segment.hoverText = text;
		}
		segments.add(segment);
	}

	private static List<TextSegment> postProcessInlineSegments(List<TextSegment> segments,
															   boolean parseCustomEmojis,
															   boolean parseUnicodeEmojis,
															   boolean parseHyperlinks) {
		if ((!parseCustomEmojis && !parseUnicodeEmojis && !parseHyperlinks) || segments.isEmpty()) {
			return segments;
		}
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.text == null || segment.text.isEmpty() || segment.clickUrl != null) {
				out.add(segment);
				continue;
			}
			List<TextSegment> current = List.of(segment);
			if (parseHyperlinks) {
				current = splitSegmentsByMarkdownLink(current);
				current = splitSegmentsByBareUrl(current);
			}
			if (parseCustomEmojis) {
				current = splitSegmentsByCustomEmoji(current);
				current = splitSegmentsByDiscordAliasEmoji(current);
			}
			if (parseUnicodeEmojis) {
				current = splitSegmentsByUnicodeEmoji(current);
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

	private static List<TextSegment> splitSegmentsByMarkdownLink(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = LINK_TOKEN_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment linkSegment = copySegment(segment, matcher.group(1));
				linkSegment.clickUrl = matcher.group(2);
				linkSegment.underlined = true;
				linkSegment.color = URL_COLOR;
				linkSegment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(linkSegment);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByBareUrl(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = BARE_URL_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment urlSegment = copySegment(segment, matcher.group(1));
				urlSegment.clickUrl = matcher.group(1);
				urlSegment.underlined = true;
				urlSegment.color = URL_COLOR;
				urlSegment.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(urlSegment);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByCustomEmoji(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = CUSTOM_EMOJI_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment emojiSegment = copySegment(segment, ":" + matcher.group(1) + ":");
				emojiSegment.color = "yellow";
				out.add(emojiSegment);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByDiscordAliasEmoji(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = DISCORD_ALIAS_EMOJI_PATTERN.matcher(segment.text);
			int cursor = 0;
			boolean matched = false;
			while (matcher.find()) {
				String alias = matcher.group();
				if (EmojiManager.getByDiscordAlias(alias).isEmpty()) {
					continue;
				}
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment emojiSegment = copySegment(segment, alias);
				emojiSegment.color = "yellow";
				out.add(emojiSegment);
				cursor = matcher.end();
				matched = true;
			}
			if (!matched) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByUnicodeEmoji(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = UNICODE_EMOJI_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				String unicodeEmoji = matcher.group();
				String alias = EmojiManager.replaceAllEmojis(unicodeEmoji, emoji -> emoji.getDiscordAliases().getFirst());
				TextSegment emojiSegment = copySegment(segment, alias);
				emojiSegment.color = "yellow";
				out.add(emojiSegment);
				cursor = matcher.end();
			}
			if (cursor == 0) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	/**
	 * Collects code block spans from the text, handling ANSI code blocks specially.
	 */
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
				for (String line : content.split("\n", 0)) {
					codeSegments.add(new TextSegment("\n  " + line));
				}
				codeSegments.add(new TextSegment("\n</code>", false, "yellow"));
			}

			spans.add(new MarkdownSpan(matcher.start(), matcher.end(), content, MarkdownType.CODE_BLOCK, codeSegments));
		}
	}

	/**
	 * Parses ANSI escape codes in content and converts them to styled TextSegments.
	 */
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
				String text = content.substring(cursor, matcher.start());
				if (!text.isEmpty()) {
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
			String text = content.substring(cursor);
			if (!text.isEmpty()) {
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
		}

		if (segments.isEmpty()) {
			segments.add(new TextSegment(content));
		}

		return segments;
	}

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
	 * Checks whether a URL appears inside a Discord spoiler wrapper in the raw message text.
	 * <p>
	 * Supports nested Markdown wrappers inside the spoiler body (for example
	 * {@code ||***https://example.com***||}) by normalizing Markdown delimiter characters.
	 *
	 * @param raw The raw Discord message content.
	 * @param url The URL to check.
	 * @return true if the URL is wrapped in {@code ||...||}; otherwise false.
	 */
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
			String normalized = content.replaceAll("[*_~`\\s]", "");
			if (url.equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	private static List<TextSegment> buildAttachmentSegments(String type, String fileName, String url, boolean spoiler) {
		List<TextSegment> segments = new ArrayList<>();
		TextSegment prefix = new TextSegment(String.format(ATTACHMENT_LABEL_PREFIX, type), false, URL_COLOR);
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
				result.add(copySegment(segment, text));
				continue;
			}
			if (newline > 0) {
				result.add(copySegment(segment, text.substring(0, newline)));
			}
			appendEllipsis(result);
			cut = true;
		}
		return result;
	}

	private static TextSegment copySegment(TextSegment source, String text) {
		TextSegment copy = new TextSegment(text, source.bold, source.color);
		copy.italic = source.italic;
		copy.underlined = source.underlined;
		copy.strikethrough = source.strikethrough;
		copy.obfuscated = source.obfuscated;
		copy.clickUrl = source.clickUrl;
		copy.hoverText = source.hoverText;
		return copy;
	}

	private static void appendEllipsis(List<TextSegment> segments) {
		if (segments.isEmpty()) {
			segments.add(new TextSegment("..."));
			return;
		}
		TextSegment tail = segments.getLast();
		segments.set(segments.size() - 1, copySegment(tail, tail.text + "..."));
	}

	/**
	 * Detects whether text contains full-width CJK characters/punctuation.
	 * Used to choose stricter truncation limits for visually wider glyphs.
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

	private static void applyDefaultColor(List<TextSegment> segments, String defaultColor) {
		if (defaultColor == null || defaultColor.isEmpty()) {
			return;
		}
		for (TextSegment seg : segments) {
			if (seg.color == null || seg.color.isEmpty()) {
				seg.color = defaultColor;
			}
		}
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
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
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

	private enum MarkdownType {
		BOLD,
		ITALIC,
		BOLD_ITALIC,
		UNDERLINE,
		STRIKETHROUGH,
		SPOILER,
		CODE_BLOCK,
		INLINE_CODE,
		HEADING
	}

	private record TokenSpan(int start, int end, TextSegment segment) {
	}

	private record MarkdownSpan(int start, int end, String innerText, MarkdownType type,
								List<TextSegment> codeBlockSegments) {
	}

	private static class MarkdownState {
		boolean bold;
		boolean italic;
		boolean underlined;
		boolean strikethrough;
		boolean obfuscated;

		MarkdownState copy() {
			MarkdownState copy = new MarkdownState();
			copy.bold = bold;
			copy.italic = italic;
			copy.underlined = underlined;
			copy.strikethrough = strikethrough;
			copy.obfuscated = obfuscated;
			return copy;
		}
	}
}
