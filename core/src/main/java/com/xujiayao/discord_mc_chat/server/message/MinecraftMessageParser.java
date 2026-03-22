package com.xujiayao.discord_mc_chat.server.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.utils.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.fellbaum.jemoji.EmojiManager;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
	private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::([tTdDfFRsS]))?>");
	private static final Pattern LINK_TOKEN_PATTERN = Pattern.compile("\\[([^]]+)]\\((https?://[^)]+)\\)");
	private static final Pattern BARE_URL_PATTERN = Pattern.compile("(https?://[^\\s*|~`<>)\\]]+)");
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

	private static final List<String> MARKDOWN_DELIMITERS = List.of("***", "~~", "||", "**", "__", "*", "_");
	private static final String URL_COLOR = "#3366CC";

	private MinecraftMessageParser() {
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
		String template = I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("mentioned").asText("{effective_name} mentioned you!");
		return template.replace("{effective_name}", senderDisplayName);
	}

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

	public static List<TextSegment> buildSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		return buildTemplateSegments(
				I18nManager.getCustomMessages().path("xxxxx_to_minecraft").path("system_message"),
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
				I18nManager.getCustomMessages().path("overwrite").path(mode).path("user_message"),
				serverName,
				effectiveName,
				roleColor,
				parsedMessageSegments
		);
	}

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
			segments = splitSegmentsByTimestamp(segments);
		}
		if (parseHyperlinks) {
			segments = splitSegmentsByMarkdownLink(segments);
			segments = splitSegmentsByBareUrl(segments);
		}
		if (parseCustomEmojis) {
			segments = splitSegmentsByCustomEmoji(segments, context);
		}
		if (parseUnicodeEmojis) {
			segments = splitSegmentsByUnicodeEmoji(segments);
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
		StringBuilder plain = new StringBuilder();

		int i = 0;
		while (i < raw.length()) {
			boolean matched = false;
			for (String delimiter : MARKDOWN_DELIMITERS) {
				if (!raw.startsWith(delimiter, i)) {
					continue;
				}
				if (isUnderscoreDelimiter(delimiter) && isInsideDiscordAliasEmoji(raw, i)) {
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
		return out.isEmpty() ? List.of(new TextSegment(raw)) : out;
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
			if (text.charAt(i) != '@' || !isMentionStartBoundary(text, i)) {
				i++;
				continue;
			}
			MentionMatch match = findMentionMatch(text, i + 1, context);
			if (match == null) {
				i++;
				continue;
			}

			if (i > cursor) {
				out.add(copySegment(segment, text.substring(cursor, i)));
			}
			TextSegment mention = copySegment(segment, "[@" + match.target.displayName + "]");
			mention.color = match.target.color;
			out.add(mention);
			context.mentionedPlayerUuids.addAll(match.target.linkedMinecraftUuids);
			if (match.target.type == MentionType.EVERYONE_HERE) {
				context.mentionEveryone = true;
			}

			i = match.endExclusive;
			cursor = i;
		}
		if (cursor == 0) {
			out.add(segment);
		} else if (cursor < text.length()) {
			out.add(copySegment(segment, text.substring(cursor)));
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByTimestamp(List<TextSegment> segments) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.clickUrl != null || segment.text == null || segment.text.isEmpty()) {
				out.add(segment);
				continue;
			}
			Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(segment.text);
			int cursor = 0;
			while (matcher.find()) {
				if (matcher.start() > cursor) {
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				String timestamp;
				try {
					long epoch = Long.parseLong(matcher.group(1));
					timestamp = "[" + formatDiscordTimestamp(epoch, matcher.group(2)) + "]";
				} catch (Exception ignored) {
					timestamp = matcher.group();
				}
				TextSegment ts = copySegment(segment, timestamp);
				ts.color = "yellow";
				out.add(ts);
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
				TextSegment link = copySegment(segment, matcher.group(1));
				link.clickUrl = matcher.group(2);
				link.underlined = true;
				link.color = URL_COLOR;
				link.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(link);
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
				TextSegment url = copySegment(segment, matcher.group(1));
				url.clickUrl = matcher.group(1);
				url.underlined = true;
				url.color = URL_COLOR;
				url.hoverText = I18nManager.getDmccTranslation("discord.message_parser.click_to_open_link");
				out.add(url);
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
					out.add(copySegment(segment, segment.text.substring(cursor, matcher.start())));
				}
				TextSegment emoji = copySegment(segment, matcher.group());
				emoji.color = "yellow";
				out.add(emoji);
				cursor = matcher.end();
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
			String text = segNode.path("text").asText("");
			boolean bold = segNode.path("bold").asBoolean(false);
			String color = segNode.path("color").asText("");

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

			List<TextSegment> contentSegments = copySegments(parsedMessageSegments);
			applyDefaultColor(contentSegments, color);
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
				if (serverName.equals(node.path("name").asText())) {
					String color = node.path("color").asText("white");
					return color == null || color.isBlank() ? "white" : color;
				}
			}
		}
		return "white";
	}

	private static List<TextSegment> copySegments(List<TextSegment> segments) {
		List<TextSegment> copy = new ArrayList<>();
		for (TextSegment seg : segments) {
			copy.add(copySegment(seg, seg.text));
		}
		return copy;
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
			if (raw.charAt(i) != '@' || !isMentionStartBoundary(raw, i)) {
				i++;
				continue;
			}
			MentionMatch match = findMentionMatch(raw, i + 1, context);
			if (match == null) {
				i++;
				continue;
			}
			out.append(raw, cursor, i);
			switch (match.target.type) {
				case USER -> out.append("<@").append(match.target.id).append(">");
				case ROLE -> out.append("<@&").append(match.target.id).append(">");
				case EVERYONE_HERE -> out.append("@").append(match.target.displayName);
			}
			i = match.endExclusive;
			cursor = i;
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
