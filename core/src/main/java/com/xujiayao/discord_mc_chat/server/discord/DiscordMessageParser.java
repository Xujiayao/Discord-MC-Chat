package com.xujiayao.discord_mc_chat.server.discord;

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

// Discord Markdown patterns (order matters: combined patterns checked before individual ones)
private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("\\*\\*\\*(.+?)\\*\\*\\*");
private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("(?<!\\\\)_(.+?)(?<!\\\\)_");
private static final Pattern ITALIC_ASTERISK_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__(.+?)__");
private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|");
private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n?([\\s\\S]*?)```");
private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
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

// Hyperlink pattern: [text](url) or bare URLs (excluding trailing markdown delimiters)
private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\((https?://[^)]+)\\)");
private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
private static final Pattern BOLD_URL_PATTERN = Pattern.compile("\\*\\*(https?://[^\\s*|~`<>)\\]]+)\\*\\*");
private static final Pattern SPOILER_URL_PATTERN = Pattern.compile("\\|\\|(https?://[^\\s*|~`<>)\\]]+)\\|\\|");

/** Maximum number of content lines for multi-line messages. */
private static final int MAX_CONTENT_LINES = 5;
private static final int REPLY_TRUNCATE_LIMIT = 20;
private static final String URL_COLOR = "#3366CC";

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
boolean isMultiLine = raw.contains("\n");

// Build segments from common.chat template
JsonNode chatNode = I18nManager.getCustomMessages().path("common").path("chat");
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

if (isMultiLine) {
// YAML-style multi-line: append "|" then newline-separated content
segments.add(new TextSegment("|", bold, color));

// Apply line limit and parse
String truncatedRaw = applyLineLimit(raw);
List<TextSegment> contentSegments = parseMessageContent(message, truncatedRaw);

// Apply default color inheritance
applyDefaultColor(contentSegments, color);

// Prepend newline to first content segment
if (!contentSegments.isEmpty()) {
contentSegments.getFirst().text = "\n" + contentSegments.getFirst().text;
}
segments.addAll(contentSegments);
} else {
List<TextSegment> contentSegments = parseMessageContent(message);

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
 * but truncated to a single line (at first newline and at 20 characters if over 50).
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
 * @param refName       referenced message author's display name
 * @param refRoleColor  referenced message author's role color
 * @param contextMessage message context for full parsing; may be null for cached/deleted messages
 * @param refRaw        raw referenced message content
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
: List.of(new TextSegment(truncatedRaw));

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
 * Parses the content of a Discord message into a list of styled text segments.
 * <p>
 * This method handles all the Discord message content types according to the
 * {@code message_parsing.discord_to_minecraft.*} config switches.
 *
 * @param message The Discord message.
 * @return The list of text segments representing the parsed message body.
 */
public static List<TextSegment> parseMessageContent(Message message) {
return parseMessageContent(message, message.getContentRaw());
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
String type = "file";
if (attachment.isImage()) {
type = "image";
} else if (attachment.isVideo()) {
type = "video";
}
String label = "<attachment type=[" + type + "] name=[" + attachment.getFileName() + "]>";

TextSegment seg = new TextSegment(label, false, "#3366CC");
seg.underlined = true;
seg.clickUrl = attachment.getUrl();
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
segments.add(seg);
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

TextSegment seg;
if (embed.getUrl() == null) {
seg = new TextSegment("<embed title=[" + title + "]>", false, "yellow");
} else {
seg = new TextSegment("<embed title=[" + title + "]>", false, "#3366CC");
seg.underlined = true;
seg.clickUrl = embed.getUrl();
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
}
segments.add(seg);
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
 boolean parseHyperlinks) {
List<TextSegment> segments = new ArrayList<>();

List<TokenSpan> tokens = new ArrayList<>();

if (parseMentions) {
collectUserMentionTokens(raw, message, tokens);
collectRoleMentionTokens(raw, message, tokens);
collectChannelMentionTokens(raw, message, tokens);
collectEveryoneHereTokens(raw, message, tokens);
}

// Collect timestamps if configured
if (ConfigManager.getBoolean("message_parsing.discord_to_minecraft.timestamps")) {
collectTimestampTokens(raw, tokens);
}

if (parseCustomEmojis) {
collectCustomEmojiTokens(raw, tokens);
}

if (parseHyperlinks) {
collectBoldUrlTokens(raw, tokens);
collectSpoilerUrlTokens(raw, tokens);
collectMarkdownLinkTokens(raw, tokens);
collectBareUrlTokens(raw, tokens);
}

if (parseUnicodeEmojis) {
collectUnicodeEmojiTokens(raw, tokens);
}

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

return segments;
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
case "t" ->
DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
.format(instant.atZone(zone));
case "T" ->
DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
.format(instant.atZone(zone));
case "d" ->
DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
.format(instant.atZone(zone));
case "D" ->
DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
.format(instant.atZone(zone));
case "s" ->
DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)
.format(instant.atZone(zone));
case "S" ->
DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).withLocale(locale)
.format(instant.atZone(zone));
case "F" ->
DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale)
.format(instant.atZone(zone));
case "R" -> {
long now = Instant.now().getEpochSecond();
long diff = now - epoch;
yield formatRelativeTime(diff);
}
default ->
DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
.format(instant.atZone(zone));
};
}

private static String formatRelativeTime(long diffSeconds) {
boolean past = diffSeconds >= 0;
long abs = Math.abs(diffSeconds);

String unit;
long value;
if (abs < 60) {
value = abs;
unit = value == 1 ? "second" : "seconds";
} else if (abs < 3600) {
value = abs / 60;
unit = value == 1 ? "minute" : "minutes";
} else if (abs < 86400) {
value = abs / 3600;
unit = value == 1 ? "hour" : "hours";
} else if (abs < 2592000) {
value = abs / 86400;
unit = value == 1 ? "day" : "days";
} else if (abs < 31536000) {
value = abs / 2592000;
unit = value == 1 ? "month" : "months";
} else {
value = abs / 31536000;
unit = value == 1 ? "year" : "years";
}

return past ? value + " " + unit + " ago" : "in " + value + " " + unit;
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

private static void collectCustomEmojiTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = CUSTOM_EMOJI_PATTERN.matcher(raw);
while (matcher.find()) {
String emojiName = matcher.group(1);
TextSegment seg = new TextSegment(":" + emojiName + ":", false, "yellow");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
}

private static void collectBoldUrlTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = BOLD_URL_PATTERN.matcher(raw);
while (matcher.find()) {
String url = matcher.group(1);
TextSegment seg = new TextSegment(url, false, URL_COLOR);
seg.bold = true;
seg.underlined = true;
seg.clickUrl = url;
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
}

private static void collectSpoilerUrlTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = SPOILER_URL_PATTERN.matcher(raw);
while (matcher.find()) {
String url = matcher.group(1);
TextSegment seg = new TextSegment(url, false, URL_COLOR);
seg.obfuscated = true;
seg.underlined = true;
seg.clickUrl = url;
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
}

private static void collectMarkdownLinkTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(raw);
while (matcher.find()) {
String linkText = matcher.group(1);
String url = matcher.group(2);
TextSegment seg = new TextSegment(linkText, false, "#3366CC");
seg.underlined = true;
seg.clickUrl = url;
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
}

private static void collectBareUrlTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = BARE_URL_PATTERN.matcher(raw);
while (matcher.find()) {
String url = matcher.group(1);
TextSegment seg = new TextSegment(url, false, "#3366CC");
seg.underlined = true;
seg.clickUrl = url;
seg.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
}

private static void collectUnicodeEmojiTokens(String raw, List<TokenSpan> tokens) {
Matcher matcher = UNICODE_EMOJI_PATTERN.matcher(raw);
while (matcher.find()) {
String unicodeEmoji = matcher.group();
String alias = EmojiManager.replaceAllEmojis(unicodeEmoji, emoji -> emoji.getDiscordAliases().getFirst());
TextSegment seg = new TextSegment(alias, false, "yellow");
tokens.add(new TokenSpan(matcher.start(), matcher.end(), seg));
}
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
collectMarkdownSpans(text, INLINE_CODE_PATTERN, MarkdownType.INLINE_CODE, spans);
collectMarkdownSpans(text, BOLD_ITALIC_PATTERN, MarkdownType.BOLD_ITALIC, spans);
collectMarkdownSpans(text, BOLD_PATTERN, MarkdownType.BOLD, spans);
collectMarkdownSpans(text, UNDERLINE_PATTERN, MarkdownType.UNDERLINE, spans);
collectMarkdownSpans(text, STRIKETHROUGH_PATTERN, MarkdownType.STRIKETHROUGH, spans);
collectMarkdownSpans(text, SPOILER_PATTERN, MarkdownType.SPOILER, spans);
collectMarkdownSpans(text, ITALIC_UNDERSCORE_PATTERN, MarkdownType.ITALIC, spans);
collectMarkdownSpans(text, ITALIC_ASTERISK_PATTERN, MarkdownType.ITALIC, spans);
collectMarkdownSpans(text, HEADING_PATTERN, MarkdownType.HEADING, spans);

spans.sort(Comparator.comparingInt(a -> a.start));
spans = removeMarkdownOverlaps(spans);

int cursor = 0;
for (MarkdownSpan span : spans) {
if (span.start > cursor) {
segments.add(new TextSegment(text.substring(cursor, span.start)));
}

switch (span.type) {
case CODE_BLOCK -> segments.addAll(span.codeBlockSegments);
case INLINE_CODE -> segments.add(new TextSegment("[" + span.innerText + "]"));
case BOLD_ITALIC -> {
TextSegment seg = new TextSegment(span.innerText);
seg.bold = true;
seg.italic = true;
segments.add(seg);
}
case HEADING -> {
TextSegment seg = new TextSegment(span.innerText);
seg.bold = true;
segments.add(seg);
}
default -> {
TextSegment seg = new TextSegment(span.innerText);
switch (span.type) {
case BOLD -> seg.bold = true;
case ITALIC -> seg.italic = true;
case UNDERLINE -> seg.underlined = true;
case STRIKETHROUGH -> seg.strikethrough = true;
case SPOILER -> seg.obfuscated = true;
default -> {}
}
segments.add(seg);
}
}

cursor = span.end;
}

if (cursor < text.length()) {
segments.add(new TextSegment(text.substring(cursor)));
}

return segments;
}

/**
 * Collects code block spans from the text, handling ANSI code blocks specially.
 */
private static void collectCodeBlockSpans(String text, List<MarkdownSpan> spans) {
Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
while (matcher.find()) {
String language = matcher.group(1);
String content = matcher.group(2);
List<TextSegment> codeSegments;

if ("ansi".equalsIgnoreCase(language) && ConfigManager.getBoolean("message_parsing.discord_to_minecraft.ansi_code_blocks")) {
codeSegments = parseAnsiContent(content);
} else {
codeSegments = List.of(new TextSegment("[" + content + "]"));
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
case 31 -> color = "dark_red";
case 32 -> color = "dark_green";
case 33 -> color = "gold";
case 34 -> color = "dark_blue";
case 35 -> color = "dark_purple";
case 36 -> color = "dark_aqua";
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

private static void collectMarkdownSpans(String text, Pattern pattern, MarkdownType type, List<MarkdownSpan> spans) {
Matcher matcher = pattern.matcher(text);
while (matcher.find()) {
spans.add(new MarkdownSpan(matcher.start(), matcher.end(), matcher.group(1), type, null));
}
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

private static String applyLineLimit(String raw) {
String[] lines = raw.split("\n");
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
int newlineIndex = raw.indexOf('\n');
int cutoff = newlineIndex >= 0 ? Math.min(newlineIndex, REPLY_TRUNCATE_LIMIT) : REPLY_TRUNCATE_LIMIT;
if (cutoff <= 0) {
return "...";
}
if (raw.length() <= cutoff) {
return raw;
}
return safeTruncate(raw, cutoff) + "...";
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
}
