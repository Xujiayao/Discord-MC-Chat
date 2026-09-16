package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.platform.Platform;
import com.xujiayao.discord_mc_chat.platform.StatsProvider;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.server.message.MessageParserCommon;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.fellbaum.jemoji.EmojiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
final class DiscordEventHandler extends ListenerAdapter {

	private static final int AUTOCOMPLETE_TIMEOUT_SECONDS = 5;
	private static final ConcurrentHashMap<String, CachedMessage> messageCache = new ConcurrentHashMap<>();
	private static final int MAX_CACHE_SIZE = 200;

	// Dedicated executor for autocomplete choice computation: the request paths underneath it block while
	// waiting for DMCC clients, so they must never run on the single-threaded JDA event pool, where they
	// would stall every other Discord event for as long as the wait lasts.
	private static volatile ExecutorService autocompleteExecutor;

	private static void logDiscordEventForConsole(Packets.DiscordRelay packet) {
		if (packet.replySegments() != null && !packet.replySegments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.replySegments()));
		}
		if (packet.segments() != null && !packet.segments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.segments()));
		}
		if (packet.eventType() == Packets.DiscordEventType.EDIT && packet.editedMessageSegments() != null && !packet.editedMessageSegments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.editedMessageSegments()));
		}
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		BotPresenceManager.update();
		ChannelUpdateManager.start();
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		event.deferReply().queue();

		int opLevel = OpLevelResolver.resolve(event.getMember(), event.getUser());
		String name = event.getName();

		switch (name) {
			case "execute" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, at, command);
			}
			case "console" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				if (at != null) {
					// standalone mode: /console <at> <command>
					CommandManager.execute(new JdaCommandSender(event, opLevel), name, at, command);
				} else {
					// single_server mode: /console <command>
					CommandManager.execute(new JdaCommandSender(event, opLevel), name, command);
				}
			}
			case "log" -> {
				String file = event.getOption("file", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, file);
			}
			case "whitelist" -> {
				String player = event.getOption("player", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, player);
			}
			case "stats" -> {
				String type = event.getOption("type", OptionMapping::getAsString);
				String stat = event.getOption("stat", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, type, stat);
			}
			case "link" -> {
				String code = event.getOption("code", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, code);
			}
			default -> CommandManager.execute(new JdaCommandSender(event, opLevel), name);
		}

		// Forward command execution notification to Minecraft (if enabled)
		boolean commandBroadcastEnabled = ConfigManager.getBoolean("broadcasts.discord_to_minecraft.command");
		if (commandBroadcastEnabled) {
			Member member = event.getMember();
			String effectiveName = member != null ? member.getEffectiveName() : event.getUser().getName();
			String roleColor = DiscordMessageAdapter.roleColorHex(member);

			StringBuilder fullCommand = new StringBuilder("/").append(name);
			for (OptionMapping option : event.getOptions()) {
				fullCommand.append(" ").append(option.getName()).append(": ").append(option.getAsString());
			}

			List<TextSegment> segments = DiscordMessageParser.buildCommandSegments(effectiveName, roleColor, fullCommand.toString());
			Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.COMMAND, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
		String commandName = event.getName();
		String focusedOption = event.getFocusedOption().getName();
		String currentValue = event.getFocusedOption().getValue();

		int opLevel = OpLevelResolver.resolve(event.getMember(), event.getUser());
		if (opLevel < ConfigManager.getInt("command_permission_levels." + commandName, 4)) {
			event.replyChoices(List.of()).queue();
			return;
		}

		// Computing the choices can block for up to AUTOCOMPLETE_TIMEOUT_SECONDS while waiting for DMCC
		// clients, so it runs on a dedicated executor and the reply is queued from there.
		try {
			autocompleteExecutor().execute(() -> event.replyChoices(computeChoices(event, commandName, focusedOption, currentValue)).queue());
		} catch (RejectedExecutionException ignored) {
			// The executor is shutting down: answer with the same empty list used when the deadline is exceeded.
			event.replyChoices(List.of()).queue();
		}
	}

	/**
	 * Computes the autocomplete choices for one interaction.
	 * <p>
	 * Runs on the autocomplete executor, never on the JDA event thread.
	 *
	 * @param event         The interaction the choices are computed for.
	 * @param commandName   Name of the command being completed.
	 * @param focusedOption Name of the option the user is currently typing.
	 * @param currentValue  The value typed into the focused option so far.
	 * @return The choices to reply with, or an empty list when the choices cannot be computed.
	 */
	private List<Command.Choice> computeChoices(CommandAutoCompleteInteractionEvent event, String commandName,
												String focusedOption, String currentValue) {
		List<Command.Choice> choices = new ArrayList<>();

		try {
			switch (commandName) {
				case "execute" -> {
					if ("at".equals(focusedOption)) {
						choices = getTargetAtChoices(currentValue);
					} else if ("command".equals(focusedOption)) {
						choices = getCommandChoices(
								(input, level) -> NetworkManager.requestExecuteAutoCompleteSnapshot(input, level, AUTOCOMPLETE_TIMEOUT_SECONDS),
								currentValue, event);
					}
				}
				case "console" -> {
					if ("at".equals(focusedOption)) {
						choices = getTargetAtChoices(currentValue);
					} else if ("command".equals(focusedOption)) {
						choices = getCommandChoices(
								(input, level) -> NetworkManager.requestConsoleAutoCompleteSnapshot(input, level, AUTOCOMPLETE_TIMEOUT_SECONDS),
								currentValue, event);
					}
				}
				case "log" -> {
					if ("file".equals(focusedOption)) {
						choices = getLogFileChoices(currentValue);
					}
				}
				case "stats" -> {
					if ("type".equals(focusedOption)) {
						choices = getStatsTypeChoices(currentValue);
					} else if ("stat".equals(focusedOption)) {
						String type = event.getOption("type", OptionMapping::getAsString);
						choices = getStatsStatChoices(type, currentValue);
					}
				}
			}
		} catch (Exception e) {
			// Nothing on the JDA event thread reports failures for this work any more, so report it here and
			// answer with the same empty list used when the deadline is exceeded.
			LOGGER.error(I18nManager.getDmccTranslation("discord.command.autocomplete_failed"), e);
			return List.of();
		}

		return choices;
	}

	private static synchronized ExecutorService autocompleteExecutor() {
		if (autocompleteExecutor == null || autocompleteExecutor.isShutdown()) {
			autocompleteExecutor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-Autocomplete"));
		}
		return autocompleteExecutor;
	}

	/**
	 * Shuts down the autocomplete executor. Called by {@link DiscordManager} while shutting the bot down.
	 */
	static synchronized void shutdown() {
		if (autocompleteExecutor != null) {
			ExecutorServiceUtils.shutdownAnExecutor(autocompleteExecutor);
			autocompleteExecutor = null;
		}
	}

	private List<Command.Choice> getTargetAtChoices(String currentValue) {
		List<Command.Choice> choices = new ArrayList<>();
		String lowerValue = currentValue.toLowerCase();

		// Add "all_online_clients" as the first option
		if ("all_online_clients".contains(lowerValue)) {
			choices.add(new Command.Choice("all_online_clients", "all_online_clients"));
		}

		// Add configured server names (only those online)
		List<String> serverNames = NetworkManager.getConnectedClientNames();
		for (String name : serverNames) {
			if (name.toLowerCase().contains(lowerValue)) {
				choices.add(new Command.Choice(name, name));
			}
		}

		// Discord limits to 25 choices
		return choices.stream().limit(25).collect(Collectors.toList());
	}

	private List<Command.Choice> getCommandChoices(BiFunction<String, Integer, Map<String, List<String>>> autoCompleteProvider,
												   String currentValue,
												   CommandAutoCompleteInteractionEvent event) {
		String target = event.getOption("at", OptionMapping::getAsString);
		int opLevel;
		if (target != null && !target.isBlank() && !"all_online_clients".equalsIgnoreCase(target)) {
			opLevel = OpLevelResolver.resolveForServer(event.getMember(), event.getUser(), target);
		} else {
			opLevel = OpLevelResolver.resolve(event.getMember(), event.getUser());
		}

		if (currentValue.startsWith("/")) {
			currentValue = currentValue.substring(1);
		}

		Map<String, List<String>> autoCompleteLists = autoCompleteProvider.apply(currentValue, opLevel);

		return autoCompleteLists.values().stream()
				.flatMap(List::stream)
				.distinct()
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	private List<Command.Choice> getLogFileChoices(String currentValue) {
		List<String> logFiles = LogFileUtils.listLogFiles();
		String lowerValue = currentValue.toLowerCase();

		return logFiles.stream()
				.filter(f -> f.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(f -> new Command.Choice(f, f))
				.collect(Collectors.toList());
	}

	private List<Command.Choice> getStatsTypeChoices(String currentValue) {
		StatsProvider provider = Platform.host().stats();
		if (provider == null) return List.of();

		String normalizedValue = StatsCommand.normalizeMinecraftNamespace(currentValue);
		String lowerValue = normalizedValue == null ? "" : normalizedValue.toLowerCase();
		return provider.getStatTypes().stream()
				.filter(t -> t.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(t -> new Command.Choice(t, t))
				.collect(Collectors.toList());
	}

	private List<Command.Choice> getStatsStatChoices(String type, String currentValue) {
		StatsProvider provider = Platform.host().stats();
		if (provider == null || type == null || type.isBlank()) return List.of();

		String normalizedType = StatsCommand.normalizeMinecraftNamespace(type);
		String normalizedValue = StatsCommand.normalizeMinecraftNamespace(currentValue);
		String lowerValue = normalizedValue == null ? "" : normalizedValue.toLowerCase();
		return provider.getStatNames(normalizedType).stream()
				.filter(s -> s.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		// Ignore messages from DMCC Bot itself
		if (event.getAuthor() == event.getJDA().getSelfUser()) {
			return;
		}

		Message message = event.getMessage();
		if (message.getType().isSystem()) {
			cacheMessage(message);
			return;
		}
		if (event.isWebhookMessage()) {
			cacheMessage(message);
			return;
		}

		if (tryExecuteConsoleMessage(event, message)) {
			cacheMessage(message);
			return;
		}

		// Check if Discord-to-Minecraft chat is enabled
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat")) {
			return;
		}

		// Only handle messages from the configured in-game-chat channel
		// Use the same channel as minecraft_to_discord player chat
		String configuredChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "in-game-chat");
		if (configuredChannel.isBlank()) {
			return;
		}

		// Check if the message is from the configured channel (by name or by ID)
		String channelId = event.getChannel().getId();
		String channelName = event.getChannel().getName();
		if (!channelId.equals(configuredChannel) && !channelName.equalsIgnoreCase(configuredChannel)) {
			return;
		}

		// Build the main message line segments using DiscordMessageParser
		List<TextSegment> mainSegments = DiscordMessageAdapter.chatSegments(message);

		// Build reply segments if this is a reply to another message
		List<TextSegment> replySegments = DiscordMessageAdapter.replySegments(message.getReferencedMessage());
		if (replySegments == null && message.getMessageReference() != null) {
			CachedMessage cachedRef = messageCache.get(message.getMessageReference().getMessageId());
			if (cachedRef != null) {
				replySegments = DiscordMessageAdapter.replySegments(
						cachedRef.authorName(),
						cachedRef.authorRoleColor(),
						null,
						cachedRef.contentRaw()
				);
				if (cachedRef.replySegments() != null && !cachedRef.replySegments().isEmpty()) {
					replySegments = cachedRef.replySegments();
				}
			}
		}

		// Build mention notification data
		String mentionNotificationText = null;
		String mentionNotificationStyle = null;
		List<String> mentionedPlayerUuids = null;

		boolean mentionNotificationsEnabled = ConfigManager.getBoolean("account_linking.mention_notifications.enable");
		boolean isMentionEveryone = DiscordMessageAdapter.isMentionEveryone(message);
		if (mentionNotificationsEnabled) {
			Set<String> uuids = DiscordMessageAdapter.collectMentionedPlayerUuids(message);
			if (isMentionEveryone || !uuids.isEmpty()) {
				Member member = message.getMember();
				String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
				mentionNotificationText = MessageParserCommon.mentionNotification(effectiveName);
				mentionNotificationStyle = ConfigManager.getString("account_linking.mention_notifications.style", "title");
				mentionedPlayerUuids = new ArrayList<>(uuids);
			}
		}

		Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.CHAT, mainSegments,
				replySegments, null, mentionNotificationText, mentionNotificationStyle,
				mentionedPlayerUuids, isMentionEveryone);

		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);

		// Cache message for edit/delete reference
		cacheMessage(message);
	}

	private boolean tryExecuteConsoleMessage(MessageReceivedEvent event, Message message) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")
				|| !ConfigManager.getBoolean("console_forwarding.execute_messages_from_channel")) {
			return false;
		}

		String targetServer = DiscordConsoleForwarder.resolveTargetServer(event.getChannel().getId(), event.getChannel().getName());
		if (targetServer == null || targetServer.isBlank()) {
			return false;
		}

		String content = message.getContentRaw();
		if (content.isBlank()) {
			return false;
		}

		if ("standalone".equals(ConfigManager.getMode())) {
			int opLevel = OpLevelResolver.resolveForServer(event.getMember(), event.getAuthor(), targetServer);
			CommandManager.execute(new MessageCommandSender(event, opLevel), "console", targetServer, content);
		} else {
			int opLevel = OpLevelResolver.resolve(event.getMember(), event.getAuthor());
			CommandManager.execute(new MessageCommandSender(event, opLevel), "console", content);
		}

		return true;
	}

	@Override
	public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.reaction")) {
			return;
		}

		String configuredChannel = getRequiredPlayerChatChannel();
		if (configuredChannel == null) {
			return;
		}

		String channelId = event.getChannel().getId();
		String channelName = event.getChannel().getName();
		if (!channelId.equals(configuredChannel) && !channelName.equalsIgnoreCase(configuredChannel)) {
			return;
		}

		Member member = event.getMember();
		if (member == null) {
			return;
		}

		String reactorName = member.getEffectiveName();
		String roleColor = DiscordMessageAdapter.roleColorHex(member);

		EmojiUnion emoji = event.getEmoji();
		String emojiText = switch (emoji.getType()) {
			case UNICODE -> EmojiManager.replaceAllEmojis(emoji.getName(), e -> e.getDiscordAliases().getFirst());
			case CUSTOM -> ":" + emoji.getName() + ":";
		};

		event.retrieveMessage().queue(targetMessage -> {
			List<TextSegment> segments = DiscordMessageParser.buildReactionSegments(reactorName, roleColor, emojiText);
			Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.REACTION, segments,
					DiscordMessageAdapter.replySegments(targetMessage), null, null, null, null, false);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		}, _ -> {
			List<TextSegment> segments = DiscordMessageParser.buildReactionSegments(reactorName, roleColor, emojiText);
			Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.REACTION, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		});
	}

	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.edit")) {
			return;
		}

		String configuredChannel = getRequiredPlayerChatChannel();
		if (configuredChannel == null) {
			return;
		}

		String channelId = event.getChannel().getId();
		String channelName = event.getChannel().getName();
		if (!channelId.equals(configuredChannel) && !channelName.equalsIgnoreCase(configuredChannel)) {
			return;
		}

		Message message = event.getMessage();
		if (message.getType().isSystem()) {
			return;
		}

		// The bot will edit message when replying slash commands
		if (event.getAuthor() == event.getJDA().getSelfUser()) {
			return;
		}

		Member member = message.getMember();
		String editorName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = DiscordMessageAdapter.roleColorHex(member);

		CachedMessage cached = messageCache.get(message.getId());
		if (cached != null && Objects.equals(cached.contentRaw(), message.getContentRaw())) {
			// Ignore metadata-only updates (e.g. pin/unpin) that do not change message text.
			return;
		}
		List<TextSegment> replySegments = null;
		if (cached != null && cached.contentRaw() != null) {
			replySegments = cached.replySegments();
			if (replySegments == null || replySegments.isEmpty()) {
				replySegments = DiscordMessageAdapter.replySegments(
						cached.authorName(),
						cached.authorRoleColor(),
						null,
						cached.contentRaw()
				);
			}
		}

		// Build edit notification segments
		List<TextSegment> notificationSegments = DiscordMessageParser.buildEditNotificationSegments(editorName, roleColor);

		// Build new message content segments
		List<TextSegment> editedMessageSegments = DiscordMessageAdapter.editedMessageSegments(message);

		Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.EDIT, notificationSegments,
				replySegments, editedMessageSegments, null, null, null, false);
		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);

		// Update cache
		cacheMessage(message);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.delete")) {
			return;
		}

		String configuredChannel = getRequiredPlayerChatChannel();
		if (configuredChannel == null) {
			return;
		}

		String channelId = event.getChannel().getId();
		String channelName = event.getChannel().getName();
		if (!channelId.equals(configuredChannel) && !channelName.equalsIgnoreCase(configuredChannel)) {
			return;
		}

		CachedMessage cached = messageCache.remove(event.getMessageId());
		if (cached != null && cached.systemMessage()) {
			return;
		}
		if (cached == null) {
			// No cached info - send a generic delete notification
			List<TextSegment> segments = DiscordMessageParser.buildDeleteSegments(I18nManager.getDmccTranslation("discord.message_parser.unknown_user"), "white");
			Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.DELETE, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
			return;
		}

		List<TextSegment> segments = DiscordMessageParser.buildDeleteSegments(cached.authorName(), cached.authorRoleColor());
		List<TextSegment> replySegments = DiscordMessageAdapter.replySegments(
				cached.authorName(),
				cached.authorRoleColor(),
				null,
				cached.contentRaw()
		);
		if (cached.replySegments() != null && !cached.replySegments().isEmpty()) {
			replySegments = cached.replySegments();
		}
		Packets.DiscordRelay packet = new Packets.DiscordRelay(Packets.DiscordEventType.DELETE, segments,
				replySegments, null, null, null, null, false);
		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);
	}

	private String getRequiredPlayerChatChannel() {
		String configPath = "broadcasts.minecraft_to_discord.player.chat";
		String configuredChannel = ConfigManager.getString(configPath, "in-game-chat");
		if (configuredChannel == null || configuredChannel.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.channel_identifier_missing", configPath));
			return null;
		}
		return configuredChannel;
	}

	private void cacheMessage(Message message) {
		// Evict entries if cache is full
		if (messageCache.size() >= MAX_CACHE_SIZE) {
			var iterator = messageCache.keySet().iterator();
			while (iterator.hasNext() && messageCache.size() >= MAX_CACHE_SIZE) {
				iterator.next();
				iterator.remove();
			}
		}

		Member member = message.getMember();
		String name = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = DiscordMessageAdapter.roleColorHex(member);
		List<TextSegment> replySegments = DiscordMessageAdapter.replySegments(message);
		messageCache.put(message.getId(), new CachedMessage(name, roleColor, message.getContentRaw(), replySegments, message.getType().isSystem()));
	}

	private record CachedMessage(String authorName, String authorRoleColor, String contentRaw,
								 List<TextSegment> replySegments,
								 boolean systemMessage) {
	}
}
