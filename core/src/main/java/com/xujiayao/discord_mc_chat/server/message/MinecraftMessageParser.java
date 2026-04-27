package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
import com.xujiayao.discord_mc_chat.utils.TextSegmentUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.fellbaum.jemoji.EmojiManager;
import tools.jackson.databind.JsonNode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses plain-text messages originating from Minecraft into:
 * <ul>
 *   <li>Discord-ready message strings (custom emoji + mention conversion)</li>
 *   <li>Minecraft-ready rich segments (markdown/emoji/mention/link/timestamp rendering)</li>
 * </ul>
 *
 * @author Xujiayao
 */
public final class MinecraftMessageParser {

	private static final Pattern SIMPLE_MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]+)(?![A-Za-z0-9_])");
	private static final Pattern DISCORD_ALIAS_EMOJI_PATTERN = Pattern.compile("(?<![A-Za-z0-9_]):([A-Za-z0-9_+\\-]+):(?![A-Za-z0-9_])");

	private static final List<String> MARKDOWN_DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");
	private static final String QUOTE_COLOR = "gray";

	private MinecraftMessageParser() {
	}

	/**
	 * Parses a player/user message.
	 *
	 * @param raw               Raw message text.
	 * @param parseForMinecraft Whether to build rich Minecraft segments.
	 * @return Parsed message container.
	 */
	public static ParsedMessage parseUserMessage(String raw, boolean parseForMinecraft) {
		return parse(raw, parseForMinecraft);
	}

	/**
	 * Parses a system message.
	 *
	 * @param raw               Raw message text.
	 * @param parseForMinecraft Whether to build rich Minecraft segments.
	 * @return Parsed message container.
	 */
	public static ParsedMessage parseSystemMessage(String raw, boolean parseForMinecraft) {
		return parse(raw, parseForMinecraft);
	}

	/**
	 * Parses a command string into display-friendly content.
	 *
	 * @param command Raw command string.
	 * @return Parsed message container.
	 */
	public static ParsedMessage parseCommandMessage(String command) {
		String discordContent = "`" + command + "`";
		List<TextSegment> mc = List.of(new TextSegment(command));
		return new ParsedMessage(discordContent, mc, Set.of(), false);
	}

	/**
	 * Builds the mention notification text shown to mentioned Minecraft players.
	 *
	 * @param senderDisplayName Display name of the mention sender.
	 * @return Localized mention notification text.
	 */
	public static String getMentionNotificationText(String senderDisplayName) {
		String template = I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("mentioned").asString("{effective_name} mentioned you!");
		return template.replace("{effective_name}", senderDisplayName);
	}

	/**
	 * Builds user-message template segments.
	 *
	 * @param serverName            Source server name.
	 * @param effectiveName         Sender display name.
	 * @param roleColor             Sender role color.
	 * @param parsedMessageSegments Parsed message body segments.
	 * @return Rendered template segments.
	 */
	public static List<TextSegment> buildUserMessageSegments(String serverName,
	                                                         String effectiveName,
	                                                         String roleColor,
	                                                         List<TextSegment> parsedMessageSegments) {
		return buildTemplateSegments(
				I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("user_message"),
				serverName,
				effectiveName,
				roleColor,
				parsedMessageSegments
		);
	}

	/**
	 * Builds system-message template segments.
	 *
	 * @param serverName            Source server name.
	 * @param parsedMessageSegments Parsed message body segments.
	 * @return Rendered template segments.
	 */
	public static List<TextSegment> buildSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		return buildTemplateSegments(
				I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("system_message"),
				serverName,
				"",
				"white",
				parsedMessageSegments
		);
	}

	/**
	 * Builds overwrite user-message template segments.
	 *
	 * @param serverName            Source server name.
	 * @param effectiveName         Sender display name.
	 * @param roleColor             Sender role color.
	 * @param parsedMessageSegments Parsed message body segments.
	 * @return Rendered overwrite template segments.
	 */
	public static List<TextSegment> buildOverwriteUserMessageSegments(String serverName,
	                                                                  String effectiveName,
	                                                                  String roleColor,
	                                                                  List<TextSegment> parsedMessageSegments) {
		String mode = ConfigManager.getString("mode", "single_server");
		return buildTemplateSegments(
				I18nManager.getCustomMessages().path("overwrite").path(mode).path("user_message"),
				serverName,
				effectiveName,
				roleColor,
				parsedMessageSegments
		);
	}

	/**
	 * Builds overwrite system-message template segments.
	 *
	 * @param serverName            Source server name.
	 * @param parsedMessageSegments Parsed message body segments.
	 * @return Rendered overwrite template segments.
	 */
	public static List<TextSegment> buildOverwriteSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		String mode = ConfigManager.getString("mode", "single_server");
		return buildTemplateSegments(
				I18nManager.getCustomMessages().path("overwrite").path(mode).path("system_message"),
				serverName,
				"",
				"white",
				parsedMessageSegments
		);
	}

	private static ParsedMessage parse(String raw, boolean parseForMinecraft) {
		String source = raw == null ? "" : raw;
		MentionContext context = buildMentionContext();

		boolean parseDiscordMentions = ConfigManager.getBoolean("message_parsing.minecraft_to_discord.mentions");
		boolean parseDiscordCustomEmojis = ConfigManager.getBoolean("message_parsing.minecraft_to_discord.custom_emojis");
		String discordContent = parseForDiscord(source, context, parseDiscordMentions, parseDiscordCustomEmojis);

		boolean parseMentions = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.mentions");
		boolean parseCustomEmojis = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.custom_emojis");
		boolean parseUnicodeEmojis = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.unicode_emojis");
		boolean parseMarkdown = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.markdown");
		boolean parseHyperlinks = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.hyperlinks");
		boolean parseTimestamps = parseForMinecraft && ConfigManager.getBoolean("message_parsing.minecraft_to_minecraft.timestamps");

		List<TextSegment> segments = parseForMinecraft
				? parseForMinecraft(source, context, parseMentions, parseCustomEmojis, parseUnicodeEmojis, parseMarkdown, parseHyperlinks, parseTimestamps)
				: List.of(new TextSegment(source));

		return new ParsedMessage(discordContent, segments, context.mentionedPlayerUuids, context.mentionEveryone);
	}

	private static String parseForDiscord(String raw,
	                                      MentionContext context,
	                                      boolean parseMentions,
	                                      boolean parseCustomEmojis) {
		if ((!parseMentions && !parseCustomEmojis) || raw.isEmpty()) {
			return raw;
		}

		String out = parseMentions ? convertMentionsForDiscord(raw, context) : raw;
		if (!parseCustomEmojis) {
			return out;
		}
		Matcher emojiMatcher = DISCORD_ALIAS_EMOJI_PATTERN.matcher(out);
		StringBuilder rebuilt = new StringBuilder(out.length() + 32);
		int cursor = 0;
		while (emojiMatcher.find()) {
			rebuilt.append(out, cursor, emojiMatcher.start());
			String emojiAlias = emojiMatcher.group(1);
			RichCustomEmoji emoji = context.customEmojiByName.get(emojiAlias.toLowerCase(Locale.ROOT));
			if (emoji != null) {
				rebuilt.append(emoji.isAnimated() ? "<a:" : "<:")
						.append(emoji.getName())
						.append(":")
						.append(emoji.getId())
						.append(">");
			} else {
				rebuilt.append(emojiMatcher.group());
			}
			cursor = emojiMatcher.end();
		}
		rebuilt.append(out.substring(cursor));
		return rebuilt.toString();
	}

	private static List<TextSegment> parseForMinecraft(String raw,
	                                                   MentionContext context,
	                                                   boolean parseMentions,
	                                                   boolean parseCustomEmojis,
	                                                   boolean parseUnicodeEmojis,
	                                                   boolean parseMarkdown,
	                                                   boolean parseHyperlinks,
	                                                   boolean parseTimestamps) {
		List<TextSegment> segments = parseMarkdown ? parseMarkdownSegments(raw) : List.of(new TextSegment(raw));

		if (parseMentions) {
			segments = splitSegmentsByMentions(segments, context);
		}
		if (parseTimestamps) {
			segments = MessageParserCommon.splitSegmentsByTimestamp(segments);
		}
		if (parseHyperlinks) {
			segments = MessageParserCommon.splitSegmentsByMarkdownLink(segments);
			segments = MessageParserCommon.splitSegmentsByBareUrl(segments);
		}
		if (parseCustomEmojis) {
			segments = splitSegmentsByCustomEmoji(segments, context);
		}
		if (parseUnicodeEmojis) {
			segments = MessageParserCommon.splitSegmentsByUnicodeEmoji(segments);
		}

		return segments;
	}

	private static MentionContext buildMentionContext() {
		Map<String, MentionTarget> userByAlias = new HashMap<>();
		Map<String, MentionTarget> roleByAlias = new HashMap<>();
		Map<String, MentionTarget> allMentionByAlias = new HashMap<>();
		Map<String, RichCustomEmoji> emojiByAlias = new HashMap<>();
		Map<String, MentionTarget> targetByDiscordId = new HashMap<>();

		Map<String, List<LinkedAccountManager.LinkEntry>> allLinks = LinkedAccountManager.getAllLinks();
		for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
			String discordId = entry.getKey();
			List<String> linkedUuids = entry.getValue().stream().map(LinkedAccountManager.LinkEntry::minecraftUuid).toList();

			User user = DiscordManager.retrieveUser(discordId);
			Member member = DiscordManager.retrieveMember(discordId);
			String displayName = member != null ? member.getEffectiveName() : (user != null ? user.getName() : discordId);
			String roleColor = DiscordMessageParser.getRoleColorHex(member);

			MentionTarget target = new MentionTarget(MentionType.USER, discordId, displayName, roleColor, linkedUuids);
			targetByDiscordId.put(discordId, target);

			if (user != null) {
				putMentionAlias(userByAlias, allMentionByAlias, user.getName(), target);
			}
			if (member != null) {
				putMentionAlias(userByAlias, allMentionByAlias, member.getEffectiveName(), target);
			}
			for (LinkedAccountManager.LinkEntry link : entry.getValue()) {
				String playerName = MojangUtils.resolvePlayerName(link.minecraftUuid(), link.offlinePlayerName());
				if (playerName != null && !playerName.isBlank()) {
					putMentionAlias(userByAlias, allMentionByAlias, playerName, target);
				}
			}
		}

		for (Member member : DiscordManager.getAllMembers()) {
			String discordId = member.getId();
			MentionTarget target = targetByDiscordId.computeIfAbsent(discordId, id -> new MentionTarget(
					MentionType.USER,
					id,
					member.getEffectiveName(),
					DiscordMessageParser.getRoleColorHex(member),
					List.of()
			));

			User user = member.getUser();
			putMentionAlias(userByAlias, allMentionByAlias, user.getName(), target);
			putMentionAlias(userByAlias, allMentionByAlias, member.getEffectiveName(), target);
		}

		for (Role role : DiscordManager.getAllRoles()) {
			String color = "white";
			Color roleColor = role.getColors().getPrimary();
			if (roleColor != null) {
				color = String.format("#%06X", roleColor.getRGB() & 0xFFFFFF);
			}
			Set<String> uuids = new HashSet<>();
			for (String discordId : DiscordManager.getDiscordIdsByRoleId(role.getId())) {
				uuids.addAll(LinkedAccountManager.getMinecraftUuidsByDiscordId(discordId));
			}
			MentionTarget roleTarget = new MentionTarget(MentionType.ROLE, role.getId(), role.getName(), color, new ArrayList<>(uuids));
			putMentionAlias(roleByAlias, allMentionByAlias, role.getName(), roleTarget);
		}

		MentionTarget everyone = new MentionTarget(MentionType.EVERYONE_HERE, "everyone", "everyone", "yellow", List.of());
		MentionTarget here = new MentionTarget(MentionType.EVERYONE_HERE, "here", "here", "yellow", List.of());
		allMentionByAlias.put("everyone", everyone);
		allMentionByAlias.put("here", here);

		for (RichCustomEmoji emoji : DiscordManager.getAllCustomEmojis()) {
			emojiByAlias.putIfAbsent(emoji.getName().toLowerCase(Locale.ROOT), emoji);
		}

		List<String> aliasesByLengthDesc = new ArrayList<>(allMentionByAlias.keySet());
		aliasesByLengthDesc.sort(Comparator.comparingInt(String::length).reversed());

		return new MentionContext(allMentionByAlias, aliasesByLengthDesc, emojiByAlias, new HashSet<>());
	}

	private static List<TextSegment> parseMarkdownSegments(String raw) {
		if (raw.isEmpty()) {
			return List.of(new TextSegment(""));
		}

		List<TextSegment> out = new ArrayList<>();
		MarkdownState state = new MarkdownState();
		int start = 0;
		while (start < raw.length()) {
			int newline = raw.indexOf('\n', start);
			int lineEnd = newline >= 0 ? newline : raw.length();
			String line = raw.substring(start, lineEnd);

			MarkdownState lineState = new MarkdownState();
			lineState.bold = state.bold;
			lineState.italic = state.italic;
			lineState.underlined = state.underlined;
			lineState.strikethrough = state.strikethrough;
			lineState.obfuscated = state.obfuscated;

			List<TextSegment> lineSegments = parseMarkdownLine(line, lineState);
			out.addAll(applyLineMarkdownDecorations(line, lineSegments));

			state.bold = lineState.bold;
			state.italic = lineState.italic;
			state.underlined = lineState.underlined;
			state.strikethrough = lineState.strikethrough;
			state.obfuscated = lineState.obfuscated;

			if (newline < 0) {
				break;
			}
			out.add(new TextSegment("\n"));
			start = newline + 1;
		}
		return out;
	}

	private static List<TextSegment> parseMarkdownLine(String raw, MarkdownState state) {
		List<TextSegment> out = new ArrayList<>();
		StringBuilder plain = new StringBuilder();

		int i = 0;
		while (i < raw.length()) {
			boolean matched = false;
			for (String delimiter : MARKDOWN_DELIMITERS) {
				if (!raw.startsWith(delimiter, i)) {
					continue;
				}
				if (MessageParserCommon.isUnderscoreDelimiter(delimiter)
						&& MessageParserCommon.isInsideDiscordAliasEmoji(raw, i, DISCORD_ALIAS_EMOJI_PATTERN)) {
					continue;
				}
				if (!shouldConsumeDelimiter(state, delimiter, raw, i)) {
					continue;
				}
				appendStyled(out, plain, state);
				toggleMarkdownState(state, delimiter);
				i += delimiter.length();
				matched = true;
				break;
			}
			if (!matched) {
				plain.append(raw.charAt(i));
				i++;
			}
		}

		appendStyled(out, plain, state);
		return out;
	}

	private static List<TextSegment> applyLineMarkdownDecorations(String line, List<TextSegment> lineSegments) {
		if (lineSegments.isEmpty()) {
			return lineSegments;
		}

		boolean quote = false;
		boolean heading = false;

		if (MessageParserCommon.isMarkdownQuoteLine(line)) {
			quote = true;
		}
		if (MessageParserCommon.isMarkdownHeadingLine(line)) {
			heading = true;
		}

		if (!quote && !heading) {
			return lineSegments;
		}

		if (quote) {
			for (TextSegment segment : lineSegments) {
				segment.color = QUOTE_COLOR;
			}
		}
		if (heading) {
			for (TextSegment segment : lineSegments) {
				segment.bold = true;
			}
		}
		return lineSegments;
	}

	private static List<TextSegment> splitSegmentsByMentions(List<TextSegment> segments, MentionContext context) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.text == null || segment.text.isEmpty() || segment.clickUrl != null) {
				out.add(segment);
				continue;
			}
			out.addAll(splitSegmentByMention(segment, context));
		}
		return out;
	}

	private static List<TextSegment> splitSegmentByMention(TextSegment segment, MentionContext context) {
		List<TextSegment> out = new ArrayList<>();
		String text = segment.text;
		int cursor = 0;
		int i = 0;
		while (i < text.length()) {
			if (text.charAt(i) == '@' && isMentionStartBoundary(text, i)) {
				MentionMatch match = findMentionMatch(text, i + 1, context);
				if (match != null) {
					if (i > cursor) {
						out.add(TextSegmentUtils.copySegment(segment, text.substring(cursor, i)));
					}
					TextSegment mention = TextSegmentUtils.copySegment(segment, "[@" + match.target.displayName + "]");
					mention.color = match.target.color;
					out.add(mention);
					context.mentionedPlayerUuids.addAll(match.target.linkedMinecraftUuids);
					if (match.target.type == MentionType.EVERYONE_HERE) {
						context.mentionEveryone = true;
					}

					i = match.endExclusive;
					cursor = i;
					continue;
				}
			}
			i++;
		}
		if (cursor == 0) {
			out.add(segment);
		} else if (cursor < text.length()) {
			out.add(TextSegmentUtils.copySegment(segment, text.substring(cursor)));
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByCustomEmoji(List<TextSegment> segments, MentionContext context) {
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
				String aliasName = matcher.group(1).toLowerCase(Locale.ROOT);
				if (!context.customEmojiByName.containsKey(aliasName) && EmojiManager.getByDiscordAlias(":" + matcher.group(1) + ":").isEmpty()) {
					continue;
				}
				matched = true;
				if (matcher.start() > cursor) {
					out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment emoji = TextSegmentUtils.copySegment(segment, matcher.group());
				emoji.color = "yellow";
				out.add(emoji);
				cursor = matcher.end();
			}
			if (!matched) {
				out.add(segment);
			} else if (cursor < segment.text.length()) {
				out.add(TextSegmentUtils.copySegment(segment, segment.text.substring(cursor)));
			}
		}
		return out;
	}

	private static void toggleMarkdownState(MarkdownState state, String delimiter) {
		switch (delimiter) {
			case "***" -> {
				state.bold = !state.bold;
				state.italic = !state.italic;
			}
			case "**" -> state.bold = !state.bold;
			case "*", "_" -> state.italic = !state.italic;
			case "__" -> state.underlined = !state.underlined;
			case "~~" -> state.strikethrough = !state.strikethrough;
			case "||" -> state.obfuscated = !state.obfuscated;
			default -> {
			}
		}
	}

	private static boolean hasClosingDelimiter(String text, int start, String delimiter) {
		for (int i = start; i <= text.length() - delimiter.length(); i++) {
			if (text.charAt(i) == '\\') {
				i++;
				continue;
			}
			if (text.startsWith(delimiter, i)) {
				return true;
			}
		}
		return false;
	}

	private static boolean shouldConsumeDelimiter(MarkdownState state, String delimiter, String text, int at) {
		if (isDelimiterActive(state, delimiter)) {
			return true;
		}
		return hasClosingDelimiter(text, at + delimiter.length(), delimiter);
	}

	private static boolean isDelimiterActive(MarkdownState state, String delimiter) {
		return switch (delimiter) {
			case "***" -> state.bold && state.italic;
			case "**" -> state.bold;
			case "*", "_" -> state.italic;
			case "__" -> state.underlined;
			case "~~" -> state.strikethrough;
			case "||" -> state.obfuscated;
			default -> false;
		};
	}

	private static void appendStyled(List<TextSegment> segments, StringBuilder plain, MarkdownState state) {
		if (plain.isEmpty()) {
			return;
		}
		TextSegment segment = new TextSegment(plain.toString());
		segment.bold = state.bold;
		segment.italic = state.italic;
		segment.underlined = state.underlined;
		segment.strikethrough = state.strikethrough;
		segment.obfuscated = state.obfuscated;
		if (segment.obfuscated) {
			segment.hoverText = segment.text;
		}
		segments.add(segment);
		plain.setLength(0);
	}

	private static List<TextSegment> buildTemplateSegments(JsonNode templateNode,
	                                                       String serverName,
	                                                       String effectiveName,
	                                                       String roleColor,
	                                                       List<TextSegment> parsedMessageSegments) {
		List<TextSegment> out = new ArrayList<>();
		if (!templateNode.isArray()) {
			return out;
		}
		for (JsonNode segNode : templateNode) {
			String text = segNode.path("text").asString("");
			boolean bold = segNode.path("bold").asBoolean(false);
			String color = segNode.path("color").asString("");

			text = replaceTemplatePlaceholders(text, serverName, effectiveName, roleColor);
			color = replaceTemplatePlaceholders(color, serverName, effectiveName, roleColor);

			if (!text.contains("{message}")) {
				out.add(new TextSegment(text, bold, color));
				continue;
			}

			String[] parts = text.split("\\{message}", -1);
			if (!parts[0].isEmpty()) {
				out.add(new TextSegment(parts[0], bold, color));
			}

			List<TextSegment> contentSegments = TextSegmentUtils.copySegments(parsedMessageSegments);
			TextSegmentUtils.applyDefaultColor(contentSegments, color);
			out.addAll(contentSegments);

			if (parts.length > 1 && !parts[1].isEmpty()) {
				out.add(new TextSegment(parts[1], bold, color));
			}
		}
		return out;
	}

	private static String replaceTemplatePlaceholders(String text, String serverName, String effectiveName, String roleColor) {
		return text.replace("{server}", serverName)
				.replace("{server_color}", getServerColor(serverName))
				.replace("{effective_name}", effectiveName)
				.replace("{display_name}", effectiveName)
				.replace("{role_color}", roleColor);
	}

	private static String getServerColor(String serverName) {
		if (!"standalone".equals(ConfigManager.getString("mode", ""))) {
			return "white";
		}
		JsonNode servers = ConfigManager.getConfigNode("multi_server.servers");
		if (servers.isArray()) {
			for (JsonNode node : servers) {
				if (serverName.equals(node.path("name").asString())) {
					String color = node.path("color").asString("white");
					return color == null || color.isBlank() ? "white" : color;
				}
			}
		}
		return "white";
	}

	private static void putMentionAlias(Map<String, MentionTarget> localMap,
	                                    Map<String, MentionTarget> allMap,
	                                    String alias,
	                                    MentionTarget target) {
		if (alias == null) {
			return;
		}
		String normalized = alias.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			return;
		}
		localMap.putIfAbsent(normalized, target);
		allMap.putIfAbsent(normalized, target);
	}

	private static String convertMentionsForDiscord(String raw, MentionContext context) {
		StringBuilder out = new StringBuilder(raw.length() + 16);
		int cursor = 0;
		int i = 0;
		while (i < raw.length()) {
			if (raw.charAt(i) == '@' && isMentionStartBoundary(raw, i)) {
				MentionMatch match = findMentionMatch(raw, i + 1, context);
				if (match != null) {
					out.append(raw, cursor, i);
					switch (match.target.type) {
						case USER -> out.append("<@").append(match.target.id).append(">");
						case ROLE -> out.append("<@&").append(match.target.id).append(">");
						case EVERYONE_HERE -> out.append("@").append(match.target.displayName);
					}
					i = match.endExclusive;
					cursor = i;
					continue;
				}
			}
			i++;
		}
		out.append(raw.substring(cursor));
		return out.toString();
	}

	private static MentionMatch findMentionMatch(String text, int contentStart, MentionContext context) {
		for (String alias : context.mentionAliasesByLengthDesc) {
			int end = contentStart + alias.length();
			if (end > text.length()) {
				continue;
			}
			if (!text.regionMatches(true, contentStart, alias, 0, alias.length())) {
				continue;
			}
			if (end < text.length() && isWordChar(text.charAt(end))) {
				continue;
			}
			MentionTarget target = context.allMentionByAlias.get(alias);
			if (target != null) {
				return new MentionMatch(target, end);
			}
		}

		Matcher simple = SIMPLE_MENTION_PATTERN.matcher(text.substring(contentStart - 1));
		if (simple.lookingAt()) {
			String token = simple.group(1).toLowerCase(Locale.ROOT);
			MentionTarget fallback = context.allMentionByAlias.get(token);
			if (fallback != null) {
				return new MentionMatch(fallback, contentStart + token.length());
			}
		}
		return null;
	}

	private static boolean isMentionStartBoundary(String text, int atIndex) {
		return atIndex == 0 || !isWordChar(text.charAt(atIndex - 1));
	}

	private static boolean isWordChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '_';
	}

	private enum MentionType {
		USER,
		ROLE,
		EVERYONE_HERE
	}

	/**
	 * Parsed message data for both Discord and Minecraft outputs.
	 *
	 * @param discordContent       Discord-ready message string.
	 * @param minecraftSegments    Minecraft-ready rich text segments.
	 * @param mentionedPlayerUuids Mentioned Minecraft player UUIDs.
	 * @param mentionEveryone      Whether an @everyone-like mention is detected.
	 */
	public record ParsedMessage(
			String discordContent,
			List<TextSegment> minecraftSegments,
			Set<String> mentionedPlayerUuids,
			boolean mentionEveryone
	) {
	}

	private record MentionTarget(MentionType type, String id, String displayName, String color,
	                             List<String> linkedMinecraftUuids) {
	}

	private record MentionMatch(MentionTarget target, int endExclusive) {
	}

	private static final class MentionContext {
		private final Map<String, MentionTarget> allMentionByAlias;
		private final List<String> mentionAliasesByLengthDesc;
		private final Map<String, RichCustomEmoji> customEmojiByName;
		private final Set<String> mentionedPlayerUuids;
		private boolean mentionEveryone;

		private MentionContext(Map<String, MentionTarget> allMentionByAlias,
		                       List<String> mentionAliasesByLengthDesc,
		                       Map<String, RichCustomEmoji> customEmojiByName,
		                       Set<String> mentionedPlayerUuids) {
			this.allMentionByAlias = allMentionByAlias;
			this.mentionAliasesByLengthDesc = mentionAliasesByLengthDesc;
			this.customEmojiByName = customEmojiByName;
			this.mentionedPlayerUuids = mentionedPlayerUuids;
			this.mentionEveryone = false;
		}
	}

	private static final class MarkdownState {
		private boolean bold;
		private boolean italic;
		private boolean underlined;
		private boolean strikethrough;
		private boolean obfuscated;
	}
}
