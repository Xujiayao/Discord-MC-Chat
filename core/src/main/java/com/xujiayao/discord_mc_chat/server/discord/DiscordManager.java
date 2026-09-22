package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
public final class DiscordManager {

	private static final int CONSOLE_FORWARDING_CHUNK_LIMIT = 1800;
	private static final int CONSOLE_FORWARDING_INLINE_LIMIT = 1200;
	private static final long PLAYER_COMMAND_RATE_LIMIT_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();
	private static final int PLAYER_COMMAND_RATE_LIMIT_MAX_MESSAGES = 10;
	private static final Object PLAYER_COMMAND_RATE_LIMIT_LOCK = new Object();
	private static final Deque<Long> PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS = new ArrayDeque<>();

	// Bounds and lifetimes of the caches below; the entity caches need an explicit lifetime because they remember IDs JDA does not know.
	private static final int ENTITY_CACHE_CAPACITY = 4096;
	private static final Duration ENTITY_CACHE_TTL = Duration.ofSeconds(60);
	private static final int ENTITY_NEGATIVE_CACHE_TTL_SECONDS = 60;
	private static final int WEBHOOK_CACHE_CAPACITY = 128;
	private static final Duration WEBHOOK_CACHE_TTL = Duration.ofMinutes(5);
	private static final int CONSOLE_FILTER_PATTERN_CACHE_CAPACITY = 256;
	private static final int DISCORD_NAME_CACHE_CAPACITY = 4096;

	// Capacity 4096 (LRU), TTL 60 s: avoids a blocking REST call per message for users not in JDA's own cache.
	private static final BoundedCache<String, User> RESOLVED_USERS = new BoundedCache<>(ENTITY_CACHE_CAPACITY, ENTITY_CACHE_TTL);
	// Capacity 4096 (LRU), TTL 60 s: negative cache for IDs that cannot be resolved (REST 404, REST outage),
	// consulted only after JDA's own cache missed, so a user JDA learns about later is still resolved immediately.
	private static final BoundedCache<String, Boolean> UNRESOLVED_USERS = new BoundedCache<>(ENTITY_CACHE_CAPACITY, Duration.ofSeconds(ENTITY_NEGATIVE_CACHE_TTL_SECONDS));
	private static final BoundedCache<String, Boolean> UNRESOLVED_MEMBERS = new BoundedCache<>(ENTITY_CACHE_CAPACITY, Duration.ofSeconds(ENTITY_NEGATIVE_CACHE_TTL_SECONDS));
	// Capacity 128 (LRU), TTL 5 min: one webhook lookup per channel instead of a retrieveWebhooks() per message;
	// an entry is also dropped when a send with it fails, so a webhook deleted on Discord's side is rebuilt next message.
	private static final BoundedCache<String, Webhook> WEBHOOK_CACHE = new BoundedCache<>(WEBHOOK_CACHE_CAPACITY, WEBHOOK_CACHE_TTL);
	// Capacity 256 (LRU), no TTL: keys are the configured regex strings themselves, and only successfully compiled
	// patterns are stored, so the per-line "invalid regex" warning is unchanged.
	private static final BoundedCache<String, Pattern> CONSOLE_FILTER_PATTERNS = new BoundedCache<>(CONSOLE_FILTER_PATTERN_CACHE_CAPACITY, null);
	// Capacity 4096 (LRU), no TTL: bounded version of the previous unbounded name cache, same "cached until evicted" semantics.
	private static final BoundedCache<String, String> DISCORD_NAME_CACHE = new BoundedCache<>(DISCORD_NAME_CACHE_CAPACITY, null);
	private static final Set<String> CONSOLE_FORWARDING_DISABLED_CLIENTS = ConcurrentHashMap.newKeySet();
	private static final Pattern EMOJI_ALIAS_PATTERN = Pattern.compile("(:[^:]+:)");
	private static JDA jda;

	private DiscordManager() {
	}

	public static boolean init() {
		// Caches may still hold entities and webhooks of a previous JDA instance.
		clearCaches();

		String token = ConfigManager.getString("discord.bot.token");
		if (token.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.token_missing"));
			return false;
		}

		// Use a custom executor with our special ThreadFactory to ensure ClassLoader is correct
		try (ExecutorService executor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordInit"))) {
			try {
				CompletableFuture<Void> readyFuture = CompletableFuture.runAsync(() -> {
					ExecutorService eventExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordEvent"));
					ExecutorService callbackExecutor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordCallback"));
					try {
						jda = JDABuilder.createDefault(token)
								.enableIntents(
										GatewayIntent.MESSAGE_CONTENT,
										GatewayIntent.GUILD_MEMBERS,
										GatewayIntent.GUILD_MESSAGE_REACTIONS
								)
								.setMemberCachePolicy(MemberCachePolicy.ALL)
								.setEventPool(eventExecutor, true)
								.setCallbackPool(callbackExecutor, true)
								.addEventListeners(new DiscordEventHandler())
								.build();

						jda.awaitReady();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
					} catch (RuntimeException e) {
						// If build() fails before JDA takes ownership of executors, shut them down to avoid leaks
						eventExecutor.shutdownNow();
						callbackExecutor.shutdownNow();
						throw e;
					}
				}, executor);

				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!readyFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.waiting_ready"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				readyFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.ready", jda.getSelfUser().getAsTag()));
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
			}

			if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
				// Don't forget to shut down the temporary executor
				executor.shutdown();
				return false;
			}

			try {
				List<CommandData> commands = new ArrayList<>();
				commands.add(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")));
				commands.add(Commands.slash("info", I18nManager.getDmccTranslation("commands.info.description")));
				commands.add(Commands.slash("log", I18nManager.getDmccTranslation("commands.log.description"))
						.addOption(OptionType.STRING, "file", I18nManager.getDmccTranslation("commands.log.args_desc.file"), true, true));
				commands.add(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")));
				commands.add(Commands.slash("update", I18nManager.getDmccTranslation("commands.update.description")));

				if ("standalone".equals(ModeManager.getMode())) {
					commands.add(Commands.slash("console", I18nManager.getDmccTranslation("commands.console.description"))
							.addOption(OptionType.STRING, "at", I18nManager.getDmccTranslation("commands.console.args_desc.at"), true, true)
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.console.args_desc.command"), true, true));
					commands.add(Commands.slash("execute", I18nManager.getDmccTranslation("commands.execute.description"))
							.addOption(OptionType.STRING, "at", I18nManager.getDmccTranslation("commands.execute.args_desc.at"), true, true)
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.execute.args_desc.command"), true, true));
					commands.add(Commands.slash("shutdown", I18nManager.getDmccTranslation("commands.shutdown.description")));
				} else {
					// single_server mode: /console <command> (no "at" parameter)
					commands.add(Commands.slash("console", I18nManager.getDmccTranslation("commands.console.description"))
							.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.console.args_desc.command"), true, true));
					commands.add(Commands.slash("stats", I18nManager.getDmccTranslation("commands.stats.description"))
							.addOption(OptionType.STRING, "type", I18nManager.getDmccTranslation("commands.stats.args_desc.type"), true, true)
							.addOption(OptionType.STRING, "stat", I18nManager.getDmccTranslation("commands.stats.args_desc.stat"), true, true));
					commands.add(Commands.slash("whitelist", I18nManager.getDmccTranslation("commands.whitelist.description"))
							.addOption(OptionType.STRING, "player", I18nManager.getDmccTranslation("commands.whitelist.args_desc.player"), true));
				}

				// Account linking commands (available in both standalone and single_server modes)
				commands.add(Commands.slash("link", I18nManager.getDmccTranslation("commands.link.description"))
						.addOption(OptionType.STRING, "code", I18nManager.getDmccTranslation("commands.link.args_desc.code"), true));
				commands.add(Commands.slash("unlink", I18nManager.getDmccTranslation("commands.unlink.description")));
				commands.add(Commands.slash("links", I18nManager.getDmccTranslation("commands.links.description")));

				CompletableFuture<List<Command>> updateFuture = jda.updateCommands().addCommands(commands).submit();
				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!updateFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.registering_commands"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				updateFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.commands_success"));
				return true;
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.commands_failed"), e);
			}
		}

		return false;
	}

	public static DiscordStatusInfo getStatusInfo() {
		if (jda == null) {
			return null;
		}

		long restPing = -1;
		try {
			restPing = jda.getRestPing().complete();
		} catch (Exception ignored) {
		}

		return new DiscordStatusInfo(
				jda.getStatus().toString(),
				jda.getSelfUser().getAsTag(),
				jda.getGatewayPing(),
				restPing
		);
	}

	static JDA getJda() {
		return jda;
	}

	/**
	 * Resolves a Discord username from a user ID via JDA, caching the result to avoid repeated blocking API calls.
	 * Falls back to the raw ID when JDA is unavailable or the user cannot be found.
	 */
	public static String resolveDiscordUserName(String discordId) {
		String cached = DISCORD_NAME_CACHE.get(discordId);
		if (cached != null) {
			return cached;
		}
		if (jda == null) {
			return discordId;
		}
		try {
			String name = jda.retrieveUserById(discordId).complete().getName();
			DISCORD_NAME_CACHE.put(discordId, name);
			return name;
		} catch (Exception e) {
			return discordId;
		}
	}

	/**
	 * Resolves a Discord user from a user ID: JDA's own cache first (kept current by gateway events, so a hit is never
	 * stale), REST only on a real miss, and a failed lookup remembered for {@value #ENTITY_NEGATIVE_CACHE_TTL_SECONDS}
	 * seconds so an unresolvable ID cannot cause one blocking REST call per message. {@code null} when unresolvable.
	 */
	public static User retrieveUser(String discordId) {
		if (jda == null) return null;

		User cached = RESOLVED_USERS.get(discordId);
		if (cached != null) {
			return cached;
		}

		User user = null;
		try {
			// MemberCachePolicy.ALL plus the GUILD_MEMBERS intent keep this cache current, so a hit is never stale.
			user = jda.getUserById(discordId);
			// The negative cache only replaces the REST fallback below; it can never hide a user JDA already knows.
			if (user == null && !Boolean.TRUE.equals(UNRESOLVED_USERS.get(discordId))) {
				user = jda.retrieveUserById(discordId).complete();
			}
		} catch (Exception ignored) {
			// Same result as before for malformed IDs and failed lookups: no user.
		}

		if (user == null) {
			// Fixed TTL: the entry is not refreshed by further misses, so the REST fallback is retried (and can recover)
			// at most once per minute per ID.
			UNRESOLVED_USERS.putIfAbsent(discordId, Boolean.TRUE);
		} else {
			RESOLVED_USERS.put(discordId, user);
		}
		return user;
	}

	/**
	 * Resolves a guild member from a user ID, scanning the guilds in the same order as before: member cache first (no
	 * REST on a hit), then the previous per-guild REST fallback, with a miss remembered for
	 * {@value #ENTITY_NEGATIVE_CACHE_TTL_SECONDS} s; {@code null} when JDA is unavailable or the user is in no known guild.
	 */
	public static Member retrieveMember(String discordId) {
		if (jda == null) return null;

		Member member = null;
		try {
			for (var guild : jda.getGuilds()) {
				member = guild.getMemberById(discordId);
				if (member != null) {
					return member;
				}
			}
		} catch (Exception ignored) {
			// Same result as before for malformed IDs: no member.
			return null;
		}

		if (Boolean.TRUE.equals(UNRESOLVED_MEMBERS.get(discordId))) {
			return null;
		}

		// Only reachable when the member is in no guild JDA knows about. JDA inserts a REST result into its own member
		// cache, so from then on the loop above resolves it from memory.
		for (var guild : jda.getGuilds()) {
			try {
				member = guild.retrieveMemberById(discordId).complete();
				if (member != null) {
					return member;
				}
			} catch (Exception ignored) {
			}
		}

		// Fixed TTL: the entry is not refreshed by further misses, so the REST fallback is retried (and can recover)
		// at most once per minute per ID.
		UNRESOLVED_MEMBERS.putIfAbsent(discordId, Boolean.TRUE);
		return null;
	}

	public static List<Member> getAllMembers() {
		if (jda == null) {
			return List.of();
		}
		return collectFromGuilds(Guild::getMembers, Member::getId);
	}

	public static List<Role> getAllRoles() {
		if (jda == null) {
			return List.of();
		}
		return collectFromGuilds(Guild::getRoles, Role::getId);
	}

	public static List<String> getDiscordIdsByRoleId(String roleId) {
		if (jda == null || roleId == null || roleId.isBlank()) {
			return List.of();
		}
		Set<String> ids = new LinkedHashSet<>();
		for (var guild : jda.getGuilds()) {
			Role role = guild.getRoleById(roleId);
			if (role == null) {
				continue;
			}
			for (Member member : guild.getMembersWithRoles(role)) {
				ids.add(member.getId());
			}
		}
		return new ArrayList<>(ids);
	}

	public static List<RichCustomEmoji> getAllCustomEmojis() {
		if (jda == null) {
			return List.of();
		}
		return collectFromGuilds(Guild::getEmojis, RichCustomEmoji::getId);
	}

	/**
	 * Sends a Minecraft user message using the configured style template; {@code channelNode} is a config node under
	 * {@code broadcasts.minecraft_to_discord} and {@code placeholders} feed the selected message template.
	 */
	public static void sendMinecraftUserMessage(String clientName, String channelNode, Map<String, String> placeholders) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			return;
		}
		if ("player.command".equals(channelNode) && !tryAcquirePlayerCommandRateLimitPermit()) {
			return;
		}

		try {
			JsonNode node = I18nManager.getCustomMessages().path("minecraft_to_discord").path("user_message");
			if (node.isMissingNode() || node.isNull()) {
				return;
			}

			String mode = "standalone".equals(ModeManager.getMode()) ? "standalone" : "single_server";
			boolean fakeUserStyle = ConfigManager.getBoolean("discord.webhook.enable_fake_user_style");

			String contentTemplate = node.path("disabled_fake_user_style").path(mode).asString("<{display_name}> {message}");
			String content = replacePlaceholders(contentTemplate, placeholders);

			for (String line : content.split("\n")) {
				LOGGER.info(sanitizeLineForLogging(line));
			}

			if (fakeUserStyle) {
				JsonNode styleNode = node.path("enabled_fake_user_style").path(mode);
				String usernameTemplate = styleNode.path("username").asString("{display_name}");
				contentTemplate = styleNode.path("content").asString("{message}");

				String username = replacePlaceholders(usernameTemplate, placeholders);
				content = replacePlaceholders(contentTemplate, placeholders);

				String avatarUrl = resolveWebhookAvatarUrl(clientName, placeholders);
				sendWebhookMessage(channel, username, avatarUrl, content);
			} else {
				sendToChannelOrWebhook(channel, channelIdentifier, clientName, content);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Sends as the client's webhook identity in standalone mode, as the bot user otherwise.
	 */
	private static void sendToChannelOrWebhook(TextChannel channel, String channelIdentifier, String clientName, String content) {
		if ("standalone".equals(ModeManager.getMode())) {
			sendWebhookMessage(channel, clientName, getClientAvatarUrl(clientName), content);
		} else {
			sendBotMessage(channelIdentifier, content);
		}
	}

	private static boolean tryAcquirePlayerCommandRateLimitPermit() {
		long now = System.nanoTime();
		synchronized (PLAYER_COMMAND_RATE_LIMIT_LOCK) {
			while (!PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS.isEmpty()
					&& now - PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS.peekFirst() >= PLAYER_COMMAND_RATE_LIMIT_WINDOW_NANOS) {
				PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS.removeFirst();
			}

			if (PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS.size() >= PLAYER_COMMAND_RATE_LIMIT_MAX_MESSAGES) {
				return false;
			}

			PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS.addLast(now);
			return true;
		}
	}

	/**
	 * Sends an already-formatted Minecraft system message; {@code channelNode} is a config node under
	 * {@code broadcasts.minecraft_to_discord}.
	 */
	public static void sendMinecraftSystemMessage(String clientName, String channelNode, String message) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			boolean standaloneMode = "standalone".equals(ModeManager.getMode());
			// A single-character pattern takes String.split's fast path (no Pattern compile) and keeps
			// the exact same trailing-empty-string behaviour as the regex "\\n" did.
			for (String line : message.split("\n")) {
				String sanitized = sanitizeLineForLogging(line);
				if (standaloneMode) {
					LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitized);
				} else {
					LOGGER.info(sanitized);
				}
			}

			sendToChannelOrWebhook(channel, channelIdentifier, clientName, message);
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	public static void sendConsoleForwardedBatchMessage(String clientName, List<String> lines) {
		if (!ConfigManager.getBoolean("console_forwarding.enable") || lines == null || lines.isEmpty()) {
			return;
		}
		if (CONSOLE_FORWARDING_DISABLED_CLIENTS.contains(clientName)) {
			return;
		}

		String channelIdentifier = resolveConsoleChannel(clientName);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}

		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			CONSOLE_FORWARDING_DISABLED_CLIENTS.add(clientName);
			return;
		}

		StringBuilder batch = new StringBuilder();
		for (String rawLine : lines) {
			for (String line : formatConsoleLinePartsForDiscord(rawLine)) {
				if (!batch.isEmpty() && batch.length() + line.length() + 1 > CONSOLE_FORWARDING_CHUNK_LIMIT) {
					sendConsoleChunk(channel, channelIdentifier, clientName, batch.toString());
					batch.setLength(0);
				}

				if (!batch.isEmpty()) {
					batch.append("\n");
				}
				batch.append(line);
			}
		}

		if (!batch.isEmpty()) {
			sendConsoleChunk(channel, channelIdentifier, clientName, batch.toString());
		}
	}

	public static void sendConsoleForwardingStatusMessage(String clientName, boolean started) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return;
		}
		if (started) {
			CONSOLE_FORWARDING_DISABLED_CLIENTS.remove(clientName);
		}
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return;
		}

		String channelIdentifier = resolveConsoleChannel(clientName);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}

		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			CONSOLE_FORWARDING_DISABLED_CLIENTS.add(clientName);
			return;
		}

		String message = buildConsoleForwardingStatusMessage(started ? "started" : "stopped", clientName);
		if (message.isBlank()) {
			return;
		}

		try {
			if ("standalone".equals(ModeManager.getMode())) {
				sendWebhookMessageSync(channel, clientName, getClientAvatarUrl(clientName), message);
			} else {
				sendBotMessageSync(channelIdentifier, message);
			}
		} catch (RejectedExecutionException ignored) {
			// JDA may reject tasks during shutdown races; ignore to avoid noisy stack traces.
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Resolves which server should run /console when a message is sent in a console forwarding channel; returns
	 * {@code null} when the channel is not configured for console forwarding.
	 */
	public static String resolveConsoleTargetServer(String channelId, String channelName) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return null;
		}

		if ("standalone".equals(ModeManager.getMode())) {
			JsonNode channels = ConfigManager.getConfigNode("console_forwarding.channels");
			if (!channels.isArray()) {
				return null;
			}
			for (int i = 0; i < channels.size(); i++) {
				JsonNode node = channels.get(i);
				String server = node.path("server").asString("").trim();
				String configuredChannel = node.path("channel").asString("").trim();
				String configPath = "console_forwarding.channels[" + i + "]";
				if (server.isBlank()) {
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", configPath + ".server"));
					continue;
				}
				if (configuredChannel.isBlank()) {
					LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", configPath + ".channel"));
					continue;
				}
				if (matchesChannelIdentifier(configuredChannel, channelId, channelName)) {
					return server;
				}
			}
			return null;
		}

		String configured = ConfigManager.getString("console_forwarding.channel", "");
		if (configured == null || configured.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", "console_forwarding.channel"));
			return null;
		}
		return matchesChannelIdentifier(configured, channelId, channelName) ? "Internal" : null;
	}

	private static String resolveConsoleChannel(String clientName) {
		if ("standalone".equals(ModeManager.getMode())) {
			JsonNode channels = ConfigManager.getConfigNode("console_forwarding.channels");
			if (!channels.isArray()) {
				return "";
			}
			for (JsonNode node : channels) {
				if (clientName.equals(node.path("server").asString(""))) {
					return node.path("channel").asString("");
				}
			}
			return "";
		}

		return ConfigManager.getString("console_forwarding.channel", "");
	}

	private static String buildConsoleForwardingStatusMessage(String key, String clientName) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		JsonNode statusNode = customMessages.path("console_forwarding").path(key);
		if (statusNode.isMissingNode() || statusNode.isNull()) {
			return "";
		}

		String mode = "standalone".equals(ModeManager.getMode()) ? "standalone" : "single_server";
		String template = statusNode.path(mode).asString(statusNode.asString(""));
		if (template.isBlank()) {
			return "";
		}

		return template.replace("{server}", clientName == null ? "" : clientName);
	}

	private static boolean matchesChannelIdentifier(String configuredChannel, String channelId, String channelName) {
		if (configuredChannel == null || configuredChannel.isBlank()) {
			return false;
		}
		return configuredChannel.equals(channelId) || configuredChannel.equalsIgnoreCase(channelName);
	}

	private static String applySensitiveRedaction(String message) {
		String output = message;
		JsonNode regexList = ConfigManager.getConfigNode("console_forwarding.filter_regex");
		if (!regexList.isArray()) {
			return output;
		}

		for (JsonNode node : regexList) {
			if (node == null || !node.isString()) {
				continue;
			}
			String regex = node.asString("");
			if (regex.isBlank()) {
				continue;
			}
			try {
				output = consoleFilterPattern(regex).matcher(output).replaceAll("redacted");
			} catch (PatternSyntaxException e) {
				LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.invalid_console_filter_regex", regex));
			}
		}

		return output;
	}

	/**
	 * Returns the compiled form of a {@code console_forwarding.filter_regex} entry, compiling each distinct regex once
	 * instead of once per console line. Invalid patterns are deliberately not cached, keeping the old per-line warning.
	 */
	private static Pattern consoleFilterPattern(String regex) {
		Pattern pattern = CONSOLE_FILTER_PATTERNS.get(regex);
		if (pattern == null) {
			pattern = Pattern.compile(regex);
			CONSOLE_FILTER_PATTERNS.put(regex, pattern);
		}
		return pattern;
	}

	private static List<String> formatConsoleLinePartsForDiscord(String rawLine) {
		String line = applySensitiveRedaction(rawLine == null ? "" : rawLine)
				.replace("\r", " ")
				.replace("\n", " ")
				.replace("`", "'");
		if (line.isBlank()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		int index = 0;
		while (index < line.length()) {
			int end = Math.min(index + CONSOLE_FORWARDING_INLINE_LIMIT, line.length());
			result.add("`" + line.substring(index, end) + "`");
			index = end;
		}
		return result;
	}

	private static void sendConsoleChunk(TextChannel channel, String channelIdentifier, String clientName, String chunk) {
		if (chunk == null || chunk.isBlank()) {
			return;
		}
		try {
			if ("standalone".equals(ModeManager.getMode())) {
				sendWebhookMessage(channel, clientName, getClientAvatarUrl(clientName), chunk);
			} else {
				sendBotMessage(channelIdentifier, chunk);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	private static String replacePlaceholders(String template, Map<String, String> placeholders) {
		String out = template;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
		}
		return out;
	}

	private static String sanitizeLineForLogging(String line) {
		// Escape underscores in :emoji: to prevent being treated as Markdown formatting
		line = EMOJI_ALIAS_PATTERN.matcher(line).replaceAll(m -> m.group().replace("_", "\\\\_"));
		return MarkdownSanitizer.sanitize(line).replace("\\_", "_");
	}

	private static <T> List<T> collectFromGuilds(Function<Guild, List<T>> extractor, Function<T, String> idExtractor) {
		Set<String> seen = new LinkedHashSet<>();
		List<T> result = new ArrayList<>();
		for (var guild : jda.getGuilds()) {
			for (T item : extractor.apply(guild)) {
				if (seen.add(idExtractor.apply(item))) {
					result.add(item);
				}
			}
		}
		return result;
	}

	private static String resolveWebhookAvatarUrl(String clientName, Map<String, String> placeholders) {
		String playerUuid = placeholders.getOrDefault("player_uuid", "");
		if (playerUuid.isBlank()) {
			return getClientAvatarUrl(clientName);
		}

		if (ConfigManager.getBoolean("account_linking.discord_user_avatar_for_webhooks")) {
			String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(playerUuid);
			if (discordId != null && !discordId.isBlank()) {
				User user = retrieveUser(discordId);
				if (user != null) {
					String avatar = user.getEffectiveAvatarUrl();
					if (!avatar.isBlank()) {
						return avatar;
					}
				}
			}
		}

		String avatarTemplate = ConfigManager.getString("discord.webhook.avatar_url", "https://mc-heads.net/avatar/{player_name}");
		return replacePlaceholders(avatarTemplate, placeholders);
	}

	public static void sendBotMessage(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(getAllowedMentions())
					.queue();
		}
	}

	public static void sendBotMessage(String channelIdentifier, String fallbackChannelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			channel = getTextChannel(fallbackChannelIdentifier);
		}
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(getAllowedMentions())
					.queue();
		}
	}

	private static void sendBotMessageSync(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(getAllowedMentions())
					.complete();
		}
	}

	private static void sendWebhookMessage(TextChannel channel, String username, String avatarUrl, String content) {
		Webhook webhook = getOrCreateWebhook(channel);

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(getAllowedMentions())
				.queue(null, failure -> handleWebhookSendFailure(channel, failure));
	}

	private static void sendWebhookMessageSync(TextChannel channel, String username, String avatarUrl, String content) {
		Webhook webhook = getOrCreateWebhook(channel);

		try {
			webhook.sendMessage(content)
					.setUsername(username)
					.setAvatarUrl(avatarUrl)
					.setAllowedMentions(getAllowedMentions())
					.complete();
		} catch (RuntimeException e) {
			// Drop the cached webhook before rethrowing, so the caller's error handling stays unchanged.
			WEBHOOK_CACHE.remove(channel.getId());
			throw e;
		}
	}

	private static void sendWebhookMessageWithFile(TextChannel channel, String username, String avatarUrl,
	                                               String content, byte[] fileData, String fileName) {
		Webhook webhook = getOrCreateWebhook(channel);

		List<Message.MentionType> allowedMentions = getAllowedMentions();

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(allowedMentions)
				.addFiles(FileUpload.fromData(fileData, fileName))
				.queue(null, failure -> handleWebhookSendFailure(channel, failure));
	}

	/**
	 * Resolves the DMCC webhook of the channel, creating it when it does not exist yet; the result is cached per
	 * channel ID, so a send no longer performs a full {@code retrieveWebhooks()} REST call. On lookup/creation failure
	 * the entry is dropped and the failure rethrown, exactly as the callers handled before.
	 */
	private static Webhook getOrCreateWebhook(TextChannel channel) {
		String channelId = channel.getId();

		Webhook cached = WEBHOOK_CACHE.get(channelId);
		if (cached != null) {
			return cached;
		}

		try {
			Webhook webhook = channel.retrieveWebhooks().complete()
					.stream()
					.filter(i -> "DMCC Webhook".equals(i.getName()))
					.filter(i -> {
						User owner = i.getOwnerAsUser();
						return owner != null && owner.getId().equals(jda.getSelfUser().getId());
					})
					.findFirst()
					.orElseGet(() -> channel.createWebhook("DMCC Webhook").complete()); // Must use orElseGet to avoid unnecessary creation
			WEBHOOK_CACHE.put(channelId, webhook);
			return webhook;
		} catch (RuntimeException e) {
			// Never keep a half-resolved entry around; the next message retries the lookup once, as before.
			WEBHOOK_CACHE.remove(channelId);
			throw e;
		}
	}

	/**
	 * Drops the cached webhook after a failed send (deleted webhook, revoked permission, ...) so the next message
	 * re-resolves it; the previous failure handling is unchanged.
	 */
	private static void handleWebhookSendFailure(TextChannel channel, Throwable failure) {
		WEBHOOK_CACHE.remove(channel.getId());
		RestAction.getDefaultFailure().accept(failure);
	}

	private static List<Message.MentionType> getAllowedMentions() {
		List<Message.MentionType> allowedMentions = new ArrayList<>();
		JsonNode allowMentionsNode = ConfigManager.getConfigNode("discord.allow_mentions");
		if (allowMentionsNode.isArray()) {
			for (JsonNode node : allowMentionsNode) {
				switch (node.asString()) {
					case "everyone" -> {
						allowedMentions.add(Message.MentionType.EVERYONE);
						allowedMentions.add(Message.MentionType.HERE);
					}
					case "users" -> allowedMentions.add(Message.MentionType.USER);
					case "roles" -> allowedMentions.add(Message.MentionType.ROLE);
				}
			}
		}
		return allowedMentions;
	}

	private static String getClientAvatarUrl(String clientName) {
		String avatarUrl = "";
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode != null && serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (clientName.equals(node.path("name").asString())) {
					avatarUrl = node.path("avatar_url").asString();
				}
			}
		}
		if (avatarUrl == null || avatarUrl.isBlank()) {
			avatarUrl = jda.getSelfUser().getEffectiveAvatarUrl();
		}
		return avatarUrl;
	}

	public static void sendExecuteResultViaWebhook(String channelIdentifier, String clientName, String message) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = getClientAvatarUrl(clientName);
			for (String block : CodeBlockMessageUtils.splitToCodeBlocks(message)) {
				sendWebhookMessage(channel, clientName, avatarUrl, block);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	public static void sendExecuteResultWithFileViaWebhook(String channelIdentifier, String clientName, String message,
	                                                       byte[] fileData, String fileName) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = getClientAvatarUrl(clientName);
			List<String> blocks = CodeBlockMessageUtils.splitToCodeBlocks(message);
			sendWebhookMessageWithFile(channel, clientName, avatarUrl, blocks.getFirst(), fileData, fileName);
			for (int i = 1; i < blocks.size(); i++) {
				sendWebhookMessage(channel, clientName, avatarUrl, blocks.get(i));
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	public static void clientBroadcast(String clientName, String channelNode, String lang, Map<String, String> placeholders) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			// User chooses not to broadcast this event
			return;
		}
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) return;

		try {
			JsonNode customMessages = I18nManager.getCustomMessages();
			if (customMessages == null) return;

			String[] parts = ("minecraft_to_xxxxx." + lang).split("\\.");
			JsonNode messageNode = customMessages;
			for (String part : parts) {
				messageNode = messageNode.path(part);
			}

			String message = messageNode.asString();

			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
			}

			if ("standalone".equals(ModeManager.getMode())) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, message);

				for (String line : message.split("\n")) {
					LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitizeLineForLogging(line));
				}
			} else {
				sendBotMessage(channelIdentifier, message);
			}
		} catch (InsufficientPermissionException e) {
			String reason = I18nManager.getDmccTranslation("discord.manager.insufficient_permission", channel.getName(), e.getPermission().getName());
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", reason));
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Sends an MSPT monitoring message to the configured monitoring channel.
	 * In standalone mode, messages are sent through each client's webhook identity.
	 * In single_server mode, messages are sent directly by the bot.
	 *
	 * @param clientName The DMCC client/server name (used for standalone webhook identity)
	 * @param message    The already-formatted message content
	 */
	public static void sendMsptMonitoringMessage(String clientName, String message) {
		String channelIdentifier = ConfigManager.getString("mspt_monitoring.channel");
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}

		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			String logReadyMessage = DiscordMessageParser.formatDiscordTimestampsForPlainText(message);

			if ("standalone".equals(ModeManager.getMode())) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, message);
				for (String line : logReadyMessage.split("\n")) {
					LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitizeLineForLogging(line));
				}
			} else {
				sendBotMessage(channelIdentifier, message);
				for (String line : logReadyMessage.split("\n")) {
					LOGGER.info(sanitizeLineForLogging(line));
				}
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	private static TextChannel getTextChannel(String identifier) {
		if (jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
			return null;
		}

		if (identifier == null || identifier.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
			return null;
		}

		TextChannel tc;
		String normalizedIdentifier = identifier.trim();

		// Try search by name first; the first result wins, which is ambiguous when several channels share the name.
		List<TextChannel> channels = jda.getTextChannelsByName(normalizedIdentifier, true);
		if (!channels.isEmpty()) {
			tc = channels.getFirst();
		} else {
			// Try parsing as ID only when the identifier is a valid snowflake.
			boolean numericId = !normalizedIdentifier.isEmpty();
			for (int i = 0; i < normalizedIdentifier.length(); i++) {
				if (!Character.isDigit(normalizedIdentifier.charAt(i))) {
					numericId = false;
					break;
				}
			}

			if (!numericId) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
				return null;
			}

			tc = jda.getTextChannelById(normalizedIdentifier);
			if (tc == null) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_not_found", identifier));
				return null;
			}
		}

		if (!tc.canTalk()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_cannot_talk", identifier));
			return null;
		}

		return tc;
	}

	public static void shutdown() {
		BotPresenceManager.shutdown();
		ChannelUpdateManager.shutdown();

		if (jda != null) {
			jda.shutdown();
			try {
				if (ConfigManager.getBoolean("shutdown.graceful_shutdown")) {
					jda.awaitShutdown(Duration.ofMinutes(10));
				} else {
					jda.awaitShutdown(Duration.ofSeconds(5));
				}
			} catch (Exception ignored) {
			}
			jda.shutdownNow();

			jda = null;
		}

		clearCaches();
	}

	/**
	 * Drops everything tied to the current JDA instance or configuration; the resolved Discord names are deliberately
	 * kept because they stay valid across a reconnect.
	 */
	private static void clearCaches() {
		RESOLVED_USERS.clear();
		UNRESOLVED_USERS.clear();
		UNRESOLVED_MEMBERS.clear();
		WEBHOOK_CACHE.clear();
		CONSOLE_FILTER_PATTERNS.clear();
	}

	/**
	 * Tiny thread-safe cache with a hard entry bound: inserting beyond {@code maxSize} evicts the least recently used
	 * entry; a non-null {@code ttl} also expires entries, while {@code null} means the key itself carries validity
	 * (e.g. a configured regex string). Operations synchronize because callers live on the Netty event loop and JDA threads.
	 */
	private static final class BoundedCache<K, V> {

		private final int maxSize;
		private final long ttlNanos;
		private final Map<K, Entry<V>> entries;

		BoundedCache(int maxSize, Duration ttl) {
			this.maxSize = maxSize;
			this.ttlNanos = ttl == null ? Long.MAX_VALUE : ttl.toNanos();
			this.entries = new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<K, BoundedCache.Entry<V>> eldest) {
					return size() > BoundedCache.this.maxSize;
				}
			};
		}

		synchronized V get(K key) {
			Entry<V> entry = entries.get(key);
			if (entry == null) {
				return null;
			}
			if (System.nanoTime() - entry.storedAtNanos() > ttlNanos) {
				entries.remove(key);
				return null;
			}
			return entry.value();
		}

		synchronized void put(K key, V value) {
			entries.put(key, new Entry<>(value, System.nanoTime()));
		}

		/** Stores only when no live entry exists, so repeated misses cannot extend the first (negative) entry's lifetime. */
		synchronized void putIfAbsent(K key, V value) {
			Entry<V> entry = entries.get(key);
			if (entry == null || System.nanoTime() - entry.storedAtNanos() > ttlNanos) {
				entries.put(key, new Entry<>(value, System.nanoTime()));
			}
		}

		synchronized void remove(K key) {
			entries.remove(key);
		}

		synchronized void clear() {
			entries.clear();
		}

		private record Entry<V>(V value, long storedAtNanos) {
		}
	}

	public record DiscordStatusInfo(String status, String tag, long gatewayPingMillis, long restPingMillis) {
	}
}
