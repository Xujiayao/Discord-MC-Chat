package com.xujiayao.discord_mc_chat.server.message;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.DiscordMessageAdapter;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
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

	private static final String CONFIG_PREFIX = "message_parsing.minecraft_to_minecraft.";
	private static final String DISCORD_PREFIX = "message_parsing.minecraft_to_discord.";

	private static final Pattern SIMPLE_MENTION = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]+)(?![A-Za-z0-9_])");

	/**
	 * How long a built mention directory stays valid, in milliseconds.
	 * <p>
	 * Building the directory walks every linked account (with blocking JDA REST lookups), every guild member
	 * and every role, so it must not run for every chat message. Guild membership, nicknames, roles and
	 * custom emojis change rarely, and a link or unlink drops the directory immediately through
	 * {@link #invalidateMentionCache()}, so one minute of staleness is the accepted upper bound.
	 */
	private static final long MENTION_DIRECTORY_TTL_MILLIS = 60_000L;
	/**
	 * Serializes rebuilds of the mention directory, so a burst of concurrent messages triggers a single
	 * rebuild and every other reader waits for that result instead of rebuilding it again.
	 */
	private static final Object mentionDirectoryLock = new Object();
	/**
	 * The cached mention directory, or null when it still has to be built.
	 * <p>
	 * A single volatile reference to an immutable value: readers see either the whole previous directory or
	 * the whole new one, and can never observe a partially built alias table.
	 */
	private static volatile MentionDirectory mentionDirectoryCache;

	private MinecraftMessageParser() {
	}

	/**
	 * Parses a message coming from Minecraft.
	 *
	 * @param parseForMinecraft When false, only the Discord-ready string is built and the Minecraft
	 *                          segments are left as one unstyled segment.
	 */
	public static ParsedMessage parseMessage(String raw, boolean parseForMinecraft) {
		return parse(raw, parseForMinecraft);
	}

	/**
	 * Parses a command string into display-friendly content: the Discord side gets it wrapped in backticks.
	 */
	public static ParsedMessage parseCommandMessage(String command) {
		return new ParsedMessage("`" + command + "`", List.of(new TextSegment(command)), Set.of(), false);
	}

	// --- Template rendering ----------------------------------------------------------------------

	/**
	 * Builds user-message template segments.
	 */
	public static List<TextSegment> buildUserMessageSegments(String serverName, String effectiveName,
															 String roleColor, List<TextSegment> parsedMessageSegments) {
		return template("xxxxx_to_minecraft", "user_message")
				.with("server", serverName)
				.with("server_color", getServerColor(serverName))
				.with("effective_name", effectiveName)
				.with("display_name", effectiveName)
				.with("role_color", roleColor)
				.content(() -> TextSegment.copyOfAll(parsedMessageSegments))
				.render();
	}

	/**
	 * Builds system-message template segments.
	 */
	public static List<TextSegment> buildSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		return template("xxxxx_to_minecraft", "system_message")
				.with("server", serverName)
				.with("server_color", getServerColor(serverName))
				.with("effective_name", "")
				.with("display_name", "")
				.with("role_color", "white")
				.content(() -> TextSegment.copyOfAll(parsedMessageSegments))
				.render();
	}

	/**
	 * Builds user-message template segments using the "overwrite" variant, which replaces the source
	 * server's own rendering instead of being relayed next to it.
	 */
	public static List<TextSegment> buildOverwriteUserMessageSegments(String serverName, String effectiveName,
																	  String roleColor, List<TextSegment> parsedMessageSegments) {
		return template("overwrite", ConfigManager.getString("mode", "single_server"), "user_message")
				.with("server", serverName)
				.with("server_color", getServerColor(serverName))
				.with("effective_name", effectiveName)
				.with("display_name", effectiveName)
				.with("role_color", roleColor)
				.content(() -> TextSegment.copyOfAll(parsedMessageSegments))
				.render();
	}

	/**
	 * Builds system-message template segments using the "overwrite" variant.
	 */
	public static List<TextSegment> buildOverwriteSystemMessageSegments(String serverName, List<TextSegment> parsedMessageSegments) {
		return template("overwrite", ConfigManager.getString("mode", "single_server"), "system_message")
				.with("server", serverName)
				.with("server_color", getServerColor(serverName))
				.with("effective_name", "")
				.with("display_name", "")
				.with("role_color", "white")
				.content(() -> TextSegment.copyOfAll(parsedMessageSegments))
				.render();
	}

	// --- Parsing ---------------------------------------------------------------------------------

	private static ParsedMessage parse(String raw, boolean parseForMinecraft) {
		String source = raw == null ? "" : raw;
		MentionContext context = buildMentionContext();

		String discordContent = parseForDiscord(source, context,
				ConfigManager.getBoolean(DISCORD_PREFIX + "mentions"),
				ConfigManager.getBoolean(DISCORD_PREFIX + "custom_emojis"));

		List<TextSegment> segments = parseForMinecraft
				? parseForMinecraft(source, context)
				: List.of(new TextSegment(source));

		return new ParsedMessage(discordContent, segments, context.mentionedPlayerUuids, context.mentionEveryone);
	}

	private static List<TextSegment> parseForMinecraft(String raw, MentionContext context) {
		List<TextSegment> segments = ConfigManager.getBoolean(CONFIG_PREFIX + "markdown")
				? MarkdownParser.parseMinecraftMarkup(raw)
				: List.of(new TextSegment(raw));

		if (ConfigManager.getBoolean(CONFIG_PREFIX + "mentions")) {
			segments = splitSegmentsByMentions(segments, context);
		}
		if (ConfigManager.getBoolean(CONFIG_PREFIX + "timestamps")) {
			segments = MessageParserCommon.splitByPattern(segments, MessageParserCommon.TIMESTAMP, MessageParserCommon::timestamp);
		}
		if (ConfigManager.getBoolean(CONFIG_PREFIX + "hyperlinks")) {
			segments = MessageParserCommon.splitByPattern(segments, MessageParserCommon.MARKDOWN_LINK,
					(matcher, source) -> MessageParserCommon.link(source, matcher.group(1), matcher.group(2)));
			segments = MessageParserCommon.splitByPattern(segments, MessageParserCommon.BARE_URL,
					(matcher, source) -> MessageParserCommon.link(source, matcher.group(1), matcher.group(1)));
		}
		if (ConfigManager.getBoolean(CONFIG_PREFIX + "custom_emojis")) {
			segments = splitSegmentsByCustomEmoji(segments, context);
		}
		if (ConfigManager.getBoolean(CONFIG_PREFIX + "unicode_emojis")) {
			segments = MessageParserCommon.splitByPattern(segments, MessageParserCommon.UNICODE_EMOJI, MessageParserCommon::unicodeEmoji);
		}
		return segments;
	}

	/**
	 * Rewrites Minecraft {@code @name} mentions as Discord mentions and {@code :alias:} emoji as custom
	 * emoji tokens.
	 */
	private static String parseForDiscord(String raw, MentionContext context, boolean parseMentions, boolean parseCustomEmojis) {
		if ((!parseMentions && !parseCustomEmojis) || raw.isEmpty()) {
			return raw;
		}

		String out = parseMentions ? convertMentionsForDiscord(raw, context) : raw;
		if (!parseCustomEmojis) {
			return out;
		}
		Matcher emojiMatcher = MessageParserCommon.ALIAS_EMOJI.matcher(out);
		StringBuilder rebuilt = new StringBuilder(out.length() + 32);
		int cursor = 0;
		while (emojiMatcher.find()) {
			rebuilt.append(out, cursor, emojiMatcher.start());
			RichCustomEmoji emoji = context.customEmojiByName.get(emojiMatcher.group(1).toLowerCase(Locale.ROOT));
			if (emoji != null) {
				rebuilt.append(emoji.isAnimated() ? "<a:" : "<:")
						.append(emoji.getName()).append(":").append(emoji.getId()).append(">");
			} else {
				rebuilt.append(emojiMatcher.group());
			}
			cursor = emojiMatcher.end();
		}
		rebuilt.append(out.substring(cursor));
		return rebuilt.toString();
	}

	private static List<TextSegment> splitSegmentsByMentions(List<TextSegment> segments, MentionContext context) {
		List<TextSegment> out = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (!MessageParserCommon.isSplittable(segment)) {
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
						out.add(TextSegment.copyOf(segment, text.substring(cursor, i)));
					}
					TextSegment mention = TextSegment.copyOf(segment, "[@" + match.target.displayName + "]");
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
			out.add(TextSegment.copyOf(segment, text.substring(cursor)));
		}
		return out;
	}

	private static List<TextSegment> splitSegmentsByCustomEmoji(List<TextSegment> segments, MentionContext context) {
		return MessageParserCommon.splitByPattern(segments, MessageParserCommon.ALIAS_EMOJI, (matcher, source) -> {
			String alias = matcher.group(1).toLowerCase(Locale.ROOT);
			if (!context.customEmojiByName.containsKey(alias)
					&& EmojiManager.getByDiscordAlias(":" + matcher.group(1) + ":").isEmpty()) {
				return null;
			}
			TextSegment emoji = TextSegment.copyOf(source, matcher.group());
			emoji.color = "yellow";
			return emoji;
		});
	}

	// --- Mention directory -----------------------------------------------------------------------

	/**
	 * Builds the per-call mention context on top of the mention directory.
	 *
	 * @return A mention context for one message.
	 */
	private static MentionContext buildMentionContext() {
		return new MentionContext(mentionDirectory());
	}

	/**
	 * Returns the mention directory, rebuilding it when the cached one has expired or was never built.
	 * <p>
	 * The rebuild is single-flight: the first caller to find the cache stale builds the directory while the
	 * other callers wait for that result. It is worth caching because walking every guild member and role is
	 * far more work than parsing one message, even though the blocking Discord lookups inside it are served
	 * from {@link DiscordManager}'s own short-lived profile cache.
	 *
	 * @return The cached or freshly built mention directory.
	 */
	private static MentionDirectory mentionDirectory() {
		MentionDirectory cached = mentionDirectoryCache;
		long now = System.currentTimeMillis();
		if (cached != null && now - cached.builtAtMillis() < MENTION_DIRECTORY_TTL_MILLIS) {
			return cached;
		}

		synchronized (mentionDirectoryLock) {
			cached = mentionDirectoryCache;
			now = System.currentTimeMillis();
			if (cached != null && now - cached.builtAtMillis() < MENTION_DIRECTORY_TTL_MILLIS) {
				return cached;
			}

			MentionDirectory rebuilt = buildMentionDirectory(now);
			mentionDirectoryCache = rebuilt;
			return rebuilt;
		}
	}

	/**
	 * Drops the cached mention directory, so the next parsed message rebuilds it.
	 * <p>
	 * Call this whenever the set of linked accounts changes, because the linked player names are part of the
	 * directory. Guild membership, nicknames, roles and custom emojis are picked up by the TTL instead.
	 */
	public static void invalidateMentionCache() {
		synchronized (mentionDirectoryLock) {
			mentionDirectoryCache = null;
		}
	}

	/**
	 * Builds the alias table used to resolve Minecraft-side {@code @name} mentions.
	 * <p>
	 * Aliases come from linked Discord accounts (user name, effective name, linked player names) and from
	 * every guild member, so that unlinked players can still be mentioned by Discord name.
	 *
	 * @param builtAtMillis The timestamp to stamp the directory with, for the TTL check.
	 */
	private static MentionDirectory buildMentionDirectory(long builtAtMillis) {
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

			MentionTarget target = new MentionTarget(MentionType.USER, discordId, displayName,
					DiscordMessageAdapter.roleColorHex(member), linkedUuids);
			targetByDiscordId.put(discordId, target);

			if (user != null) {
				putMentionAlias(allMentionByAlias, user.getName(), target);
			}
			if (member != null) {
				putMentionAlias(allMentionByAlias, member.getEffectiveName(), target);
			}
			for (LinkedAccountManager.LinkEntry link : entry.getValue()) {
				String playerName = MojangUtils.resolvePlayerName(link.minecraftUuid(), link.offlinePlayerName());
				if (playerName != null && !playerName.isBlank()) {
					putMentionAlias(allMentionByAlias, playerName, target);
				}
			}
		}

		List<Member> allMembers = DiscordManager.getAllMembers();
		for (Member member : allMembers) {
			String discordId = member.getId();
			MentionTarget target = targetByDiscordId.computeIfAbsent(discordId, id -> new MentionTarget(
					MentionType.USER,
					id,
					member.getEffectiveName(),
					DiscordMessageAdapter.roleColorHex(member),
					List.of()
			));
			putMentionAlias(allMentionByAlias, member.getUser().getName(), target);
			putMentionAlias(allMentionByAlias, member.getEffectiveName(), target);
		}

		List<Role> allRoles = DiscordManager.getAllRoles();
		for (Role role : allRoles) {
			String color = "white";
			Color roleColor = role.getColors().getPrimary();
			if (roleColor != null) {
				color = String.format("#%06X", roleColor.getRGB() & 0xFFFFFF);
			}
			Set<String> uuids = new HashSet<>();
			for (String discordId : DiscordManager.getDiscordIdsByRoleId(role.getId())) {
				uuids.addAll(LinkedAccountManager.getMinecraftUuidsByDiscordId(discordId));
			}
			putMentionAlias(allMentionByAlias, role.getName(),
					new MentionTarget(MentionType.ROLE, role.getId(), role.getName(), color, new ArrayList<>(uuids)));
		}

		allMentionByAlias.put("everyone", new MentionTarget(MentionType.EVERYONE_HERE, "everyone", "everyone", "yellow", List.of()));
		allMentionByAlias.put("here", new MentionTarget(MentionType.EVERYONE_HERE, "here", "here", "yellow", List.of()));

		List<RichCustomEmoji> allCustomEmojis = DiscordManager.getAllCustomEmojis();
		for (RichCustomEmoji emoji : allCustomEmojis) {
			emojiByAlias.putIfAbsent(emoji.getName().toLowerCase(Locale.ROOT), emoji);
		}

		List<String> aliasesByLengthDesc = new ArrayList<>(allMentionByAlias.keySet());
		aliasesByLengthDesc.sort(Comparator.comparingInt(String::length).reversed());

		return new MentionDirectory(allMentionByAlias, aliasesByLengthDesc, emojiByAlias, builtAtMillis);
	}

	private static void putMentionAlias(Map<String, MentionTarget> aliases, String alias, MentionTarget target) {
		if (alias == null) {
			return;
		}
		String normalized = alias.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty()) {
			aliases.putIfAbsent(normalized, target);
		}
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
			if (end > text.length() || !text.regionMatches(true, contentStart, alias, 0, alias.length())) {
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

		Matcher simple = SIMPLE_MENTION.matcher(text.substring(contentStart - 1));
		if (simple.lookingAt()) {
			MentionTarget fallback = context.allMentionByAlias.get(simple.group(1).toLowerCase(Locale.ROOT));
			if (fallback != null) {
				return new MentionMatch(fallback, contentStart + simple.group(1).length());
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

	// --- Helpers ---------------------------------------------------------------------------------

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

	private static MessageTemplates.Builder template(String... path) {
		JsonNode node = I18nManager.getCustomMessages();
		for (String key : path) {
			node = node.path(key);
		}
		return MessageTemplates.of(node);
	}

	private enum MentionType {
		USER,
		ROLE,
		EVERYONE_HERE
	}

	/**
	 * Parsed message data for both Discord and Minecraft outputs.
	 */
	public record ParsedMessage(
			String discordContent,
			List<TextSegment> minecraftSegments,
			Set<String> mentionedPlayerUuids,
			boolean mentionEveryone
	) {
	}

	/**
	 * Mention lookup data, rebuilt lazily and shared by every message until it expires.
	 * <p>
	 * The tables are only ever read after the directory has been published to {@code mentionDirectoryCache},
	 * and a rebuild always installs a brand new directory, so no reader can observe one being filled in.
	 *
	 * @param mentionAliasesByLengthDesc Aliases ordered longest first, so a longer name wins over a prefix
	 *                                   of it.
	 * @param builtAtMillis              Build timestamp, compared against the TTL.
	 */
	private record MentionDirectory(Map<String, MentionTarget> allMentionByAlias,
									List<String> mentionAliasesByLengthDesc,
									Map<String, RichCustomEmoji> customEmojiByName,
									long builtAtMillis) {
	}

	private record MentionTarget(MentionType type, String id, String displayName, String color,
								 List<String> linkedMinecraftUuids) {
	}

	private record MentionMatch(MentionTarget target, int endExclusive) {
	}

	/**
	 * Parsing state of one message: the shared directory plus the two values that belong to the message
	 * currently being parsed.
	 */
	private static final class MentionContext {
		private final Map<String, MentionTarget> allMentionByAlias;
		private final List<String> mentionAliasesByLengthDesc;
		private final Map<String, RichCustomEmoji> customEmojiByName;
		/**
		 * Per call: the Minecraft UUIDs mentioned by this one message.
		 */
		private final Set<String> mentionedPlayerUuids = new HashSet<>();
		/**
		 * Per call: whether this one message mentioned everyone/here.
		 */
		private boolean mentionEveryone;

		private MentionContext(MentionDirectory directory) {
			this.allMentionByAlias = directory.allMentionByAlias();
			this.mentionAliasesByLengthDesc = directory.mentionAliasesByLengthDesc();
			this.customEmojiByName = directory.customEmojiByName();
		}
	}
}
