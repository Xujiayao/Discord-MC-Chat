package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Discord using JDA (Java Discord API).
 *
 * @author Xujiayao
 */
public final class DiscordManager {

	private static final long PLAYER_COMMAND_RATE_LIMIT_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();
	private static final int PLAYER_COMMAND_RATE_LIMIT_MAX_MESSAGES = 10;
	private static final Object PLAYER_COMMAND_RATE_LIMIT_LOCK = new Object();
	private static final Deque<Long> PLAYER_COMMAND_RATE_LIMIT_TIMESTAMPS = new ArrayDeque<>();

	private static final Map<String, String> DISCORD_NAME_CACHE = new ConcurrentHashMap<>();

	/**
	 * How long a lookup of a Discord user or guild member stays cached.
	 */
	private static final long PROFILE_TTL_MILLIS = 60_000L;

	private static final Object PROFILE_CACHE_LOCK = new Object();
	private static volatile ProfileCache profileCache;
	private static volatile long profileCacheCreatedAt;

	private static JDA jda;

	private DiscordManager() {
	}

	/**
	 * Initializes the Discord bot.
	 *
	 * @return true when the bot connected and its slash commands are registered.
	 */
	public static boolean init() {
		String token = ConfigManager.getString("discord.bot.token");
		if (token.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.token_missing"));
			return false;
		}

		// Use a custom executor with our special ThreadFactory to ensure ClassLoader is correct
		try (ExecutorService executor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordInit"))) {
			try {
				// Blocks until JDA is ready
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

			// Blocks until commands are updated
			try {
				List<CommandData> commands = new ArrayList<>();
				commands.add(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")));
				commands.add(Commands.slash("info", I18nManager.getDmccTranslation("commands.info.description")));
				commands.add(Commands.slash("log", I18nManager.getDmccTranslation("commands.log.description"))
						.addOption(OptionType.STRING, "file", I18nManager.getDmccTranslation("commands.log.args_desc.file"), true, true));
				commands.add(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")));
				commands.add(Commands.slash("update", I18nManager.getDmccTranslation("commands.update.description")));

				if ("standalone".equals(ConfigManager.getMode())) {
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

	/**
	 * Retrieves the current Discord status info.
	 *
	 * @return The DiscordStatusInfo, or null if JDA is not ready
	 */
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

	/**
	 * Gets the JDA instance.
	 *
	 * @return The JDA instance, or null if not initialized
	 */
	static JDA getJda() {
		return jda;
	}

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
	 * Retrieves a Discord User object by ID.
	 * <p>
	 * The result is cached for {@link #PROFILE_TTL_MILLIS}. Resolving an ID that JDA does not hold is a
	 * blocking REST call, and callers such as the mention directory ask for every linked account, so an
	 * uncached lookup here runs once per chat message.
	 *
	 * @return The User object, or null if JDA is not available or user not found.
	 */
	public static User retrieveUser(String discordId) {
		if (jda == null) return null;
		return profiles().user(discordId);
	}

	/**
	 * Retrieves a Discord Member object by user ID from the first mutual guild.
	 * <p>
	 * The result is cached for {@link #PROFILE_TTL_MILLIS}, because a member that JDA does not hold has to
	 * be fetched over REST.
	 *
	 * @return The Member object, or null if JDA is not available or member not found.
	 */
	public static Member retrieveMember(String discordId) {
		if (jda == null) return null;
		return profiles().member(discordId);
	}

	/**
	 * A short-lived snapshot of the Discord users and guild members that DMCC has looked up.
	 * <p>
	 * Reads and writes are both guarded, but a miss is resolved while holding the lock on purpose: two
	 * threads that want the same uncached user must not both pay for the REST round trip. The entries are
	 * cleared whenever the bot reconnects, so the cache never outlives the JDA instance it was filled from.
	 */
	private static final class ProfileCache {

		private final Map<String, User> users = new HashMap<>();
		private final Map<String, Optional<Member>> members = new HashMap<>();

		private User user(String discordId) {
			synchronized (this) {
				if (users.containsKey(discordId)) {
					return users.get(discordId);
				}
			}
			User resolved = null;
			try {
				resolved = jda.retrieveUserById(discordId).complete();
			} catch (Exception ignored) {
			}
			synchronized (this) {
				users.put(discordId, resolved);
			}
			return resolved;
		}

		private Member member(String discordId) {
			synchronized (this) {
				Optional<Member> cached = members.get(discordId);
				if (cached != null) {
					return cached.orElse(null);
				}
			}
			Optional<Member> resolved = Optional.empty();
			for (var guild : jda.getGuilds()) {
				try {
					Member member = guild.retrieveMemberById(discordId).complete();
					if (member != null) {
						resolved = Optional.of(member);
						break;
					}
				} catch (Exception ignored) {
				}
			}
			synchronized (this) {
				members.put(discordId, resolved);
			}
			return resolved.orElse(null);
		}
	}

	/**
	 * @return The current profile snapshot, replacing an expired one with a fresh empty cache.
	 */
	private static ProfileCache profiles() {
		ProfileCache current = profileCache;
		long now = System.currentTimeMillis();
		if (current != null && now - profileCacheCreatedAt < PROFILE_TTL_MILLIS) {
			return current;
		}
		synchronized (PROFILE_CACHE_LOCK) {
			if (profileCache == null || System.currentTimeMillis() - profileCacheCreatedAt >= PROFILE_TTL_MILLIS) {
				profileCache = new ProfileCache();
				profileCacheCreatedAt = System.currentTimeMillis();
			}
			return profileCache;
		}
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
	 * Sends a Minecraft user-originated message to Discord using the configured style template.
	 *
	 * @param clientName   DMCC client/server name.
	 * @param channelNode  Config node under {@code broadcasts.minecraft_to_discord}.
	 * @param placeholders Placeholder values used by the selected message template.
	 */
	public static void sendMinecraftUserMessage(String clientName, String channelNode, Map<String, String> placeholders) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}
		TextChannel channel = DiscordSender.find(channelIdentifier);
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

			String mode = "standalone".equals(ConfigManager.getMode()) ? "standalone" : "single_server";
			boolean fakeUserStyle = ConfigManager.getBoolean("discord.webhook.enable_fake_user_style");

			String contentTemplate = node.path("disabled_fake_user_style").path(mode).asString("<{display_name}> {message}");
			String content = replacePlaceholders(contentTemplate, placeholders);

			for (String line : content.split("\n")) {
				// Escape underscores in :emoji: to prevent being treated as Markdown formatting
				LOGGER.info(DiscordSender.sanitizeLineForLogging(line));
			}

			if (fakeUserStyle) {
				JsonNode styleNode = node.path("enabled_fake_user_style").path(mode);
				String usernameTemplate = styleNode.path("username").asString("{display_name}");
				contentTemplate = styleNode.path("content").asString("{message}");

				String username = replacePlaceholders(usernameTemplate, placeholders);
				content = replacePlaceholders(contentTemplate, placeholders);

				String avatarUrl = resolveWebhookAvatarUrl(clientName, placeholders);
				DiscordSender.send(channel, username, avatarUrl, content, true);
			} else {
				if ("standalone".equals(ConfigManager.getMode())) {
					String avatarUrl = DiscordSender.clientAvatarUrl(clientName);
					DiscordSender.send(channel, clientName, avatarUrl, content, true);
				} else {
					sendBotMessage(channelIdentifier, null, content);
				}
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
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
	 * Sends an already-formatted Minecraft system message to Discord.
	 *
	 * @param clientName  DMCC client/server name.
	 * @param channelNode Config node under {@code broadcasts.minecraft_to_discord}.
	 * @param message     Already formatted message content.
	 */
	public static void sendMinecraftSystemMessage(String clientName, String channelNode, String message) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			return;
		}
		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			DiscordSender.postServerMessage(channel, channelIdentifier, clientName, message, message);
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
			return DiscordSender.clientAvatarUrl(clientName);
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

	/**
	 * Sends a message to the specified Discord channel identifier using the bot account.
	 *
	 * @param fallbackChannelIdentifier Fallback channel name or ID if the primary identifier fails to resolve.
	 */
	public static void sendBotMessage(String channelIdentifier, String fallbackChannelIdentifier, String content) {
		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null && fallbackChannelIdentifier != null) {
			channel = DiscordSender.find(fallbackChannelIdentifier);
		}
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(DiscordSender.allowedMentions())
					.queue();
		}
	}

	static void sendBotMessageSync(String channelIdentifier, String content) {
		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content)
					.setAllowedMentions(DiscordSender.allowedMentions())
					.complete();
		}
	}


	public static void sendExecuteResultViaWebhook(String channelIdentifier, String clientName, String message) {
		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = DiscordSender.clientAvatarUrl(clientName);
			for (String block : CodeBlockMessageUtils.splitToCodeBlocks(message)) {
				DiscordSender.send(channel, clientName, avatarUrl, block, true);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	public static void sendExecuteResultWithFileViaWebhook(String channelIdentifier, String clientName, String message,
														   byte[] fileData, String fileName) {
		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) return;

		try {
			String avatarUrl = DiscordSender.clientAvatarUrl(clientName);
			List<String> blocks = CodeBlockMessageUtils.splitToCodeBlocks(message);
			DiscordSender.sendWithFile(channel, clientName, avatarUrl, blocks.getFirst(), fileData, fileName, true);
			for (int i = 1; i < blocks.size(); i++) {
				DiscordSender.send(channel, clientName, avatarUrl, blocks.get(i), true);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Broadcasts a Minecraft system event to its configured Discord channel, using the template stored
	 * under {@code custom_messages.minecraft_to_xxxxx.<lang>}.
	 */
	public static void clientBroadcast(String clientName, String channelNode, String lang, Map<String, String> placeholders) {
		String channelIdentifier = ConfigManager.getString("broadcasts.minecraft_to_discord." + channelNode);
		if (channelIdentifier == null || channelIdentifier.isBlank()) {
			// User chooses not to broadcast this event
			return;
		}
		TextChannel channel = DiscordSender.find(channelIdentifier);
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
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}

			if ("standalone".equals(ConfigManager.getMode())) {
				DiscordSender.postServerMessage(channel, channelIdentifier, clientName, message, message);
			} else {
				sendBotMessage(channelIdentifier, null, message);
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

		TextChannel channel = DiscordSender.find(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			String logReadyMessage = DiscordMessageParser.formatDiscordTimestampsForPlainText(message);
			DiscordSender.postServerMessage(channel, channelIdentifier, clientName, message, logReadyMessage);
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}


	public static void shutdown() {
		BotPresenceManager.shutdown();
		ChannelUpdateManager.shutdown();
		DiscordEventHandler.shutdown();

		// The cached webhook handles and Discord profile lookups belong to the JDA instance that is going away.
		DiscordSender.clearWebhookCache();
		synchronized (PROFILE_CACHE_LOCK) {
			profileCache = null;
		}

		if (jda != null) {
			jda.shutdown();
			try {
				if (ConfigManager.getBoolean("shutdown.graceful_shutdown")) {
					// Allow up to 10 minutes for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofMinutes(10));
				} else {
					// Allow up to 5 seconds for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofSeconds(5));
				}
			} catch (Exception ignored) {
			}
			jda.shutdownNow();

			jda = null;
		}
	}

	public record DiscordStatusInfo(String status, String tag, long gatewayPingMillis, long restPingMillis) {
	}
}
