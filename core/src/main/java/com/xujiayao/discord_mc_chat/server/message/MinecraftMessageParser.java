package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.MessageParserCommon.MarkdownState;
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
 * Parses plain-text messages originating from Minecraft into Discord-ready message strings and
 * Minecraft-ready rich segments.
 */
public final class MinecraftMessageParser {

	private static final Pattern SIMPLE_MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]+)(?![A-Za-z0-9_])");
	private static final Pattern DISCORD_ALIAS_EMOJI_PATTERN = Pattern.compile("(?<![A-Za-z0-9_]):([A-Za-z0-9_+\\-]+):(?![A-Za-z0-9_])");

	private static final Pattern MESSAGE_PLACEHOLDER_PATTERN = Pattern.compile("\\{message}");

	private static final List<String> MARKDOWN_DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");
	private static final String QUOTE_COLOR = "gray";

	private MinecraftMessageParser() {
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

	public static ParsedMessage parseUserMessage(String raw, boolean parseForMinecraft) {
		return parse(raw, parseForMinecraft);
	}

	public static ParsedMessage parseSystemMessage(String raw, boolean parseForMinecraft) {
		return parse(raw, parseForMinecraft);
	}

	public static ParsedMessage parseCommandMessage(String command) {
		String discordContent = "`" + command + "`";
		List<TextSegment> mc = List.of(new TextSegment(command));
		return new ParsedMessage(discordContent, mc, Set.of(), false);
	}

	public static String getMentionNotificationText(String senderDisplayName) {
		JsonNode mentionedNode = customMessageNode("xxxxx_to_minecraft", "mentioned");
		String template = mentionedNode == null ? "{effective_name} mentioned you!" : mentionedNode.asString("{effective_name} mentioned you!");
		return template.replace("{effective_name}", senderDisplayName);
	}

	public static List<TextSegment> buildUserMessageSegments(String serverName,
	                                                         String effectiveName,
	                                                         String roleColor,
	                                                         List<TextSegment> parsedMessageSegments) {
		return buildTemplateSegments(
				customMessageNode("xxxxx_to_minecraft", "user_message"),
				serverName,
				effectiveName,
				roleColor,
				parsedMessageSegments
		);
	}

	public static List<TextSegment> buildSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		return buildTemplateSegments(
				customMessageNode("xxxxx_to_minecraft", "system_message"),
				serverName,
				"",
				"white",
				parsedMessageSegments
		);
	}

	public static List<TextSegment> buildOverwriteUserMessageSegments(String serverName,
	                                                                  String effectiveName,
	                                                                  String roleColor,
	                                                                  List<TextSegment> parsedMessageSegments) {
		String mode = ConfigManager.getString("mode", "single_server");
		return buildTemplateSegments(
				customMessageNode("overwrite", mode, "user_message"),
				serverName,
				effectiveName,
				roleColor,
				parsedMessageSegments
		);
	}

	public static List<TextSegment> buildOverwriteSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		String mode = ConfigManager.getString("mode", "single_server");
		return buildTemplateSegments(
				customMessageNode("overwrite", mode, "system_message"),
				serverName,
				"",
				"white",
				parsedMessageSegments
		);
	}

	private static ParsedMessage parse(String raw, boolean parseForMinecraft) {
		String source = raw == null ? "" : raw;
		// The mention and emoji tables are built lazily on first actual use, so a message without any
		// '@' or ':alias:' never walks linked accounts, members, roles or emojis.
		MentionContext context = new MentionContext();

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
			RichCustomEmoji emoji = context.customEmojiNames().get(emojiAlias.toLowerCase(Locale.ROOT));
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

	/**
	 * Only called when a message really contains a mention candidate, because it walks every linked
	 * account, member and role. Members come from JDA's event-maintained cache (the same member the REST
	 * lookup returned for guild members); the two REST lookups stay as the fallback for unknown ids.
	 */
	private static void buildMentionTables(MentionContext context) {
		Map<String, MentionTarget> userByAlias = new HashMap<>();
		Map<String, MentionTarget> roleByAlias = new HashMap<>();
		Map<String, MentionTarget> allMentionByAlias = new HashMap<>();
		Map<String, MentionTarget> targetByDiscordId = new HashMap<>();

		List<Member> allMembers = DiscordManager.getAllMembers();
		Map<String, Member> membersById = new HashMap<>();
		for (Member member : allMembers) {
			membersById.put(member.getId(), member);
		}

		Map<String, List<LinkedAccountManager.LinkEntry>> allLinks = LinkedAccountManager.getAllLinks();
		for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
			String discordId = entry.getKey();
			List<String> linkedUuids = entry.getValue().stream().map(LinkedAccountManager.LinkEntry::minecraftUuid).toList();

			Member member = membersById.get(discordId);
			User user;
			if (member != null) {
				user = member.getUser();
			} else {
				user = DiscordManager.retrieveUser(discordId);
				member = DiscordManager.retrieveMember(discordId);
			}
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

		for (Member member : allMembers) {
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
				color = "#%06X".formatted(roleColor.getRGB() & 0xFFFFFF);
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

		// Longest alias first, as before, additionally bucketed by first character to avoid scanning all aliases.
		List<String> aliasesByLengthDesc = new ArrayList<>(allMentionByAlias.keySet());
		aliasesByLengthDesc.sort(Comparator.comparingInt(String::length).reversed());

		Map<Character, List<String>> aliasesByFirstChar = new HashMap<>();
		for (String alias : aliasesByLengthDesc) {
			aliasesByFirstChar.computeIfAbsent(Character.toUpperCase(alias.charAt(0)), firstChar -> new ArrayList<>()).add(alias);
		}

		context.allMentionByAlias = allMentionByAlias;
		context.mentionAliasesByFirstChar = aliasesByFirstChar;
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

			MarkdownState lineState = state.copy();

			List<TextSegment> lineSegments = parseMarkdownLine(line, lineState);
			out.addAll(applyLineMarkdownDecorations(line, lineSegments));

			state = lineState;

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
		ClosingDelimiterLookup closingDelimiters = new ClosingDelimiterLookup(raw);

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
				if (!shouldConsumeDelimiter(state, delimiter, i, closingDelimiters)) {
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
		return MessageParserCommon.splitSegments(segments, DISCORD_ALIAS_EMOJI_PATTERN, (segment, matcher) -> {
			String aliasName = matcher.group(1).toLowerCase(Locale.ROOT);
			// Same order as before: the custom emoji table first, the jemoji lookup only as fallback.
			if (!context.customEmojiNames().containsKey(aliasName) && !context.hasJemojiAlias(":" + matcher.group(1) + ":")) {
				return null;
			}
			TextSegment emoji = TextSegmentUtils.copySegment(segment, matcher.group());
			emoji.color = "yellow";
			return emoji;
		});
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

	private static boolean shouldConsumeDelimiter(MarkdownState state, String delimiter, int at, ClosingDelimiterLookup closingDelimiters) {
		if (isDelimiterActive(state, delimiter)) {
			return true;
		}
		return closingDelimiters.hasClosingDelimiter(at + delimiter.length(), delimiter);
	}

	/**
	 * Answers "does a closing delimiter follow?" in O(1) after one lazy O(n) scan per delimiter instead
	 * of rescanning from every occurrence. The escape-aware walk is unchanged (a backslash skips the next
	 * character, otherwise advance by one, stopping past the last fitting position); reachable[i] is the
	 * first delimiter occurrence reachable from i, or -1.
	 */
	private static final class ClosingDelimiterLookup {
		private final String text;
		private final Map<String, int[]> reachableByDelimiter = new HashMap<>();

		private ClosingDelimiterLookup(String text) {
			this.text = text;
		}

		private boolean hasClosingDelimiter(int start, String delimiter) {
			int limit = text.length() - delimiter.length();
			if (start > limit) {
				return false;
			}
			return reachableByDelimiter.computeIfAbsent(delimiter, this::scan)[start] >= 0;
		}

		private int[] scan(String delimiter) {
			int limit = text.length() - delimiter.length();
			int[] reachable = new int[limit + 1];
			for (int i = limit; i >= 0; i--) {
				if (text.charAt(i) == '\\') {
					int next = i + 2;
					reachable[i] = next <= limit ? reachable[next] : -1;
				} else if (text.startsWith(delimiter, i)) {
					reachable[i] = i;
				} else {
					reachable[i] = i + 1 <= limit ? reachable[i + 1] : -1;
				}
			}
			return reachable;
		}
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
		if (templateNode == null || !templateNode.isArray()) {
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

			String[] parts = MESSAGE_PLACEHOLDER_PATTERN.split(text, -1);
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
		context.ensureMentionTables();

		// Bucket lookup instead of a scan over every alias: regionMatches(ignoreCase) compares the
		// first character with Character.toUpperCase, so only aliases in that bucket can match, and
		// the bucket keeps the original longest-alias-first order.
		List<String> candidates = contentStart < text.length()
				? context.mentionAliasesByFirstChar.get(Character.toUpperCase(text.charAt(contentStart)))
				: null;
		if (candidates != null) {
			for (String alias : candidates) {
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
		}

		// A region instead of a copied substring: both start the match right after the '@'.
		Matcher simple = SIMPLE_MENTION_PATTERN.matcher(text).region(contentStart - 1, text.length());
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
	 * Parsed message for both the Discord and the Minecraft output.
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
		private final Set<String> mentionedPlayerUuids = new HashSet<>();
		private boolean mentionEveryone;

		// Lazily filled, valid only until the end of this parse; never cached across messages because
		// account link/unlink or Discord-side member/role/emoji changes have no invalidation hook here.
		private Map<String, MentionTarget> allMentionByAlias;
		private Map<Character, List<String>> mentionAliasesByFirstChar;
		private Map<String, RichCustomEmoji> customEmojiByName;

		// Per-context cache; remembering an answer cannot go stale because the jemoji table is static at runtime.
		private final Map<String, Boolean> jemojiAliasQueries = new HashMap<>();

		private void ensureMentionTables() {
			if (allMentionByAlias == null) {
				buildMentionTables(this);
			}
		}

		private Map<String, RichCustomEmoji> customEmojiNames() {
			if (customEmojiByName == null) {
				Map<String, RichCustomEmoji> emojisByName = new HashMap<>();
				for (RichCustomEmoji emoji : DiscordManager.getAllCustomEmojis()) {
					emojisByName.putIfAbsent(emoji.getName().toLowerCase(Locale.ROOT), emoji);
				}
				customEmojiByName = emojisByName;
			}
			return customEmojiByName;
		}

		private boolean hasJemojiAlias(String discordAlias) {
			return jemojiAliasQueries.computeIfAbsent(discordAlias, alias -> !EmojiManager.getByDiscordAlias(alias).isEmpty());
		}
	}
}
