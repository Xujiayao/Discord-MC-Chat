package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
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
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;
import java.util.ArrayList;
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
 * Manages Discord using JDA (Java Discord API).
 *
 * @author Xujiayao
 */
public final class DiscordManager {

	private static final int CONSOLE_FORWARDING_CHUNK_LIMIT = 1800;
	private static final int CONSOLE_FORWARDING_INLINE_LIMIT = 1200;

	private static final Map<String, String> DISCORD_NAME_CACHE = new ConcurrentHashMap<>();
	private static final Set<String> CONSOLE_FORWARDING_DISABLED_CLIENTS = ConcurrentHashMap.newKeySet();
	private static final Pattern EMOJI_ALIAS_PATTERN = Pattern.compile("(:[^:]+:)");
	private static JDA jda;

	private DiscordManager() {
	}

	/**
	 * Initializes the Discord bot.
	 *
	 * @return true if initialization is successful, false otherwise.
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

	/**
	 * Resolves a Discord username from a user ID via JDA.
	 * Results are cached in memory to avoid repeated blocking API calls.
	 * Falls back to the raw ID if JDA is not available or the user cannot be found.
	 *
	 * @param discordId The Discord user ID.
	 * @return The resolved username, or the raw ID if resolution fails.
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
	 * Retrieves a Discord User object by ID.
	 *
	 * @param discordId The Discord user ID.
	 * @return The User object, or null if JDA is not available or user not found.
	 */
	public static User retrieveUser(String discordId) {
		if (jda == null) return null;
		try {
			return jda.retrieveUserById(discordId).complete();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Retrieves a Discord Member object by user ID from the first mutual guild.
	 *
	 * @param discordId The Discord user ID.
	 * @return The Member object, or null if JDA is not available or member not found.
	 */
	public static Member retrieveMember(String discordId) {
		if (jda == null) return null;
		for (var guild : jda.getGuilds()) {
			try {
				Member member = guild.retrieveMemberById(discordId).complete();
				if (member != null) {
					return member;
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	/**
	 * Gets all Discord members from every connected guild.
	 *
	 * @return Deduplicated member list from all connected guilds.
	 */
	public static List<Member> getAllMembers() {
		if (jda == null) {
			return List.of();
		}
		return collectFromGuilds(Guild::getMembers, Member::getId);
	}

	/**
	 * Gets all guild roles from every connected guild.
	 *
	 * @return Deduplicated role list from all connected guilds.
	 */
	public static List<Role> getAllRoles() {
		if (jda == null) {
			return List.of();
		}
		return collectFromGuilds(Guild::getRoles, Role::getId);
	}

	/**
	 * Gets all Discord user IDs for members that currently have the specified role.
	 *
	 * @param roleId Discord role ID.
	 * @return User ID list for members owning the role.
	 */
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

	/**
	 * Gets all custom emojis from every connected guild.
	 *
	 * @return Deduplicated custom emoji list from all connected guilds.
	 */
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
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			JsonNode node = I18nManager.getCustomMessages().path("minecraft_to_discord").path("user_message");
			if (node.isMissingNode() || node.isNull()) {
				return;
			}

			String mode = "standalone".equals(ModeManager.getMode()) ? "standalone" : "single_server";
			boolean fakeUserStyle = ConfigManager.getBoolean("discord.webhook.enable_fake_user_style");

			String contentTemplate = node.path("disabled_fake_user_style").path(mode).asText("<{display_name}> {message}");
			String content = replacePlaceholders(contentTemplate, placeholders);

			for (String line : content.split("\n")) {
				// Escape underscores in :emoji: to prevent being treated as Markdown formatting
				LOGGER.info(sanitizeLineForLogging(line));
			}

			if (fakeUserStyle) {
				JsonNode styleNode = node.path("enabled_fake_user_style").path(mode);
				String usernameTemplate = styleNode.path("username").asText("{display_name}");
				contentTemplate = styleNode.path("content").asText("{message}");

				String username = replacePlaceholders(usernameTemplate, placeholders);
				content = replacePlaceholders(contentTemplate, placeholders);

				String avatarUrl = resolveWebhookAvatarUrl(clientName, placeholders);
				sendWebhookMessage(channel, username, avatarUrl, content);
			} else {
				if ("standalone".equals(ModeManager.getMode())) {
					String avatarUrl = getClientAvatarUrl(clientName);
					sendWebhookMessage(channel, clientName, avatarUrl, content);
				} else {
					sendBotMessage(channelIdentifier, content);
				}
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
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
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel == null) {
			return;
		}

		try {
			boolean standaloneMode = "standalone".equals(ModeManager.getMode());
			for (String line : message.split("\\n")) {
				String sanitized = sanitizeLineForLogging(line);
				if (standaloneMode) {
					LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitized);
				} else {
					LOGGER.info(sanitized);
				}
			}

			if (standaloneMode) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, message);
			} else {
				sendBotMessage(channelIdentifier, message);
			}
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getLocalizedMessage()), e);
		}
	}

	/**
	 * Sends batched console logs to the configured console forwarding channel.
	 *
	 * @param clientName DMCC client/server name.
	 * @param lines      Console log lines to forward.
	 */
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

	/**
	 * Sends a localized reminder message when console forwarding starts/stops.
	 *
	 * @param clientName DMCC client/server name.
	 * @param started    true when forwarding starts; false when it stops.
	 */
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
	 * Resolves which server should run /console when a message is sent in a console forwarding channel.
	 *
	 * @param channelId   Discord channel ID.
	 * @param channelName Discord channel name.
	 * @return Target server name, or {@code null} if channel is not configured for console forwarding.
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
				String server = node.path("server").asText("").trim();
				String configuredChannel = node.path("channel").asText("").trim();
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
				if (clientName.equals(node.path("server").asText(""))) {
					return node.path("channel").asText("");
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
		String template = statusNode.path(mode).asText(statusNode.asText(""));
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
			if (node == null || !node.isTextual()) {
				continue;
			}
			String regex = node.asText("");
			if (regex.isBlank()) {
				continue;
			}
			try {
				output = Pattern.compile(regex).matcher(output).replaceAll("redacted");
			} catch (PatternSyntaxException e) {
				LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.invalid_console_filter_regex", regex));
			}
		}

		return output;
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

	private static void sendBotMessage(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
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
		// Find or create webhook
		Webhook webhook = getOrCreateWebhook(channel);

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(getAllowedMentions())
				.queue();
	}

	private static void sendWebhookMessageSync(TextChannel channel, String username, String avatarUrl, String content) {
		Webhook webhook = getOrCreateWebhook(channel);

		webhook.sendMessage(content)
				.setUsername(username)
				.setAvatarUrl(avatarUrl)
				.setAllowedMentions(getAllowedMentions())
				.complete();
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
				.queue();
	}

	private static Webhook getOrCreateWebhook(TextChannel channel) {
		return channel.retrieveWebhooks().complete()
				.stream()
				.filter(i -> "DMCC Webhook".equals(i.getName()))
				.filter(i -> i.getOwnerAsUser() == jda.getSelfUser())
				.findFirst()
				.orElseGet(() -> channel.createWebhook("DMCC Webhook").complete()); // Must use orElseGet to avoid unnecessary creation
	}

	private static List<Message.MentionType> getAllowedMentions() {
		List<Message.MentionType> allowedMentions = new ArrayList<>();
		JsonNode allowMentionsNode = ConfigManager.getConfigNode("discord.allow_mentions");
		if (allowMentionsNode.isArray()) {
			for (JsonNode node : allowMentionsNode) {
				switch (node.asText()) {
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
				if (clientName.equals(node.path("name").asText())) {
					avatarUrl = node.path("avatar_url").asText();
				}
			}
		}
		if (avatarUrl == null || avatarUrl.isBlank()) {
			avatarUrl = jda.getSelfUser().getEffectiveAvatarUrl();
		}
		return avatarUrl;
	}

	/**
	 * Sends an execute command result via webhook to the specified Discord channel.
	 *
	 * @param channelIdentifier The target Discord channel identifier.
	 * @param clientName        The name of the DMCC client.
	 * @param message           The result message.
	 */
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

	/**
	 * Sends an execute command result with a file attachment via webhook to the specified Discord channel.
	 *
	 * @param channelIdentifier The target Discord channel identifier.
	 * @param clientName        The name of the DMCC client.
	 * @param message           The result message.
	 * @param fileData          The file data.
	 * @param fileName          The file name.
	 */
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

	/**
	 * Broadcasts a message to a Discord channel based on the specified parameters.
	 *
	 * @param clientName   The name of the DMCC client.
	 * @param channelNode  The broadcast channel identifier.
	 * @param lang         The language key for the message.
	 * @param placeholders A map of placeholders to replace in the message.
	 */
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

			String message = messageNode.asText();

			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}

			if ("standalone".equals(ModeManager.getMode())) {
				String avatarUrl = getClientAvatarUrl(clientName);
				sendWebhookMessage(channel, clientName, avatarUrl, message);

				for (String line : message.split("\n")) {
					// Escape underscores in :emoji: to prevent being treated as Markdown formatting
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
				for (String line : logReadyMessage.split("\\n")) {
					LOGGER.info(StringUtils.format("[{}] {}"), clientName, sanitizeLineForLogging(line));
				}
			} else {
				sendBotMessage(channelIdentifier, message);
				for (String line : logReadyMessage.split("\\n")) {
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

		// Try search by name
		// Return first result. Use with caution if multiple channels have the same name.
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

	/**
	 * Shuts down the Discord bot.
	 */
	public static void shutdown() {
		BotPresenceManager.shutdown();
		ChannelUpdateManager.shutdown();

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

	/**
	 * Data holder for Discord status info.
	 *
	 * @param status            JDA status string
	 * @param tag               Bot user tag
	 * @param gatewayPingMillis Gateway ping in milliseconds
	 * @param restPingMillis    REST ping in milliseconds
	 */
	public record DiscordStatusInfo(String status, String tag, long gatewayPingMillis, long restPingMillis) {
	}
}
