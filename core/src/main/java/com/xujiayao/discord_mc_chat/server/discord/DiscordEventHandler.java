package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordRelayPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
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
import java.util.stream.Collectors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public final class DiscordEventHandler extends ListenerAdapter {

	private static final int AUTOCOMPLETE_TIMEOUT_SECONDS = 5;

	/**
	 * Cache of recent messages for edit/delete reference. Maps message ID to cached data.
	 */
	private static final ConcurrentHashMap<String, CachedMessage> messageCache = new ConcurrentHashMap<>();
	private static final int MAX_CACHE_SIZE = 200;

	private static void logDiscordEventForConsole(DiscordRelayPacket packet) {
		if (packet.replySegments != null && !packet.replySegments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.replySegments));
		}
		if (packet.segments != null && !packet.segments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.segments));
		}
		if (packet.type == DiscordRelayPacket.EventType.EDIT && packet.editedMessageSegments != null && !packet.editedMessageSegments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(packet.editedMessageSegments));
		}
	}

	/**
	 * Resolves the OP Level credential for a Discord user based on config mappings.
	 *
	 * @param member The Discord Member object (null if in DMs).
	 * @param user   The Discord User object.
	 * @return The resolved OP level (-1 to 4).
	 */
	private int getOpLevel(Member member, User user) {
		return OpLevelResolver.resolve(member, user);
	}

	/**
	 * Resolves the OP Level credential for a specific target server.
	 *
	 * @param member     The Discord Member object (null if in DMs).
	 * @param user       The Discord User object.
	 * @param serverName The target DMCC client server name.
	 * @return The resolved OP level (-1 to 4).
	 */
	private int getOpLevelForServer(Member member, User user, String serverName) {
		return OpLevelResolver.resolveForServer(member, user, serverName);
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		DiscordManager.updateBotPresence();
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		event.deferReply().queue();

		int opLevel = getOpLevel(event.getMember(), event.getUser());
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
			String roleColor = DiscordMessageParser.getRoleColorHex(member);

			StringBuilder fullCommand = new StringBuilder("/").append(name);
			for (OptionMapping option : event.getOptions()) {
				fullCommand.append(" ").append(option.getName()).append(": ").append(option.getAsString());
			}

			List<TextSegment> segments = DiscordMessageParser.buildCommandSegments(effectiveName, roleColor, fullCommand.toString());
			DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.COMMAND, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
		String commandName = event.getName();
		String focusedOption = event.getFocusedOption().getName();
		String currentValue = event.getFocusedOption().getValue();

		int opLevel = getOpLevel(event.getMember(), event.getUser());
		if (opLevel < ConfigManager.getInt("command_permission_levels." + commandName, 4)) {
			event.replyChoices(List.of()).queue();
			return;
		}

		List<Command.Choice> choices = new ArrayList<>();

		switch (commandName) {
			case "execute" -> {
				if ("at".equals(focusedOption)) {
					choices = getTargetAtChoices(currentValue);
				} else if ("command".equals(focusedOption)) {
					choices = getExecuteCommandChoices(currentValue, event);
				}
			}
			case "console" -> {
				if ("at".equals(focusedOption)) {
					choices = getTargetAtChoices(currentValue);
				} else if ("command".equals(focusedOption)) {
					choices = getConsoleCommandChoices(currentValue, event);
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

		event.replyChoices(choices).queue();
	}

	/**
	 * Gets auto-complete choices for the 'at' parameter (shared by execute and console).
	 * Includes "all_online_clients" as the first option, followed by configured server names.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
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

	/**
	 * Gets auto-complete choices for the 'command' parameter of the execute command.
	 * Sends a real-time auto-complete request to connected clients with the current input and OP level,
	 * so clients can provide DMCC command suggestions that the user is authorized to execute.
	 * <p>
	 * When a target server is selected, uses the per-server OP level for accurate suggestions.
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getExecuteCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		String target = event.getOption("at", OptionMapping::getAsString);
		int opLevel;
		if (target != null && !target.isBlank() && !"all_online_clients".equalsIgnoreCase(target)) {
			opLevel = getOpLevelForServer(event.getMember(), event.getUser(), target);
		} else {
			opLevel = getOpLevel(event.getMember(), event.getUser());
		}

		if (currentValue.startsWith("/")) {
			currentValue = currentValue.substring(1);
		}

		Map<String, List<String>> autoCompleteLists = NetworkManager.requestExecuteAutoCompleteSnapshot(currentValue, opLevel, AUTOCOMPLETE_TIMEOUT_SECONDS);

		return autoCompleteLists.values().stream()
				.flatMap(List::stream)
				.distinct()
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'command' parameter of the console command.
	 * Sends a real-time auto-complete request to connected clients with the current input and OP level,
	 * so clients can provide Minecraft command suggestions via their Brigadier dispatcher.
	 * <p>
	 * When a target server is selected, uses the per-server OP level for accurate suggestions.
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getConsoleCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		String target = event.getOption("at", OptionMapping::getAsString);
		int opLevel;
		if (target != null && !target.isBlank() && !"all_online_clients".equalsIgnoreCase(target)) {
			opLevel = getOpLevelForServer(event.getMember(), event.getUser(), target);
		} else {
			opLevel = getOpLevel(event.getMember(), event.getUser());
		}

		if (currentValue.startsWith("/")) {
			currentValue = currentValue.substring(1);
		}

		Map<String, List<String>> autoCompleteLists = NetworkManager.requestConsoleAutoCompleteSnapshot(currentValue, opLevel, AUTOCOMPLETE_TIMEOUT_SECONDS);

		return autoCompleteLists.values().stream()
				.flatMap(List::stream)
				.distinct()
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'file' parameter of the log command.
	 * In standalone mode, lists DMCC log files locally.
	 * Otherwise, lists Minecraft log files.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getLogFileChoices(String currentValue) {
		List<String> logFiles = LogFileUtils.listLogFiles();
		String lowerValue = currentValue.toLowerCase();

		return logFiles.stream()
				.filter(f -> f.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(f -> new Command.Choice(f, f))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'type' parameter of the stats command.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getStatsTypeChoices(String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null) return List.of();

		String lowerValue = currentValue.toLowerCase();
		return provider.getStatTypes().stream()
				.filter(t -> t.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(t -> new Command.Choice(t, t))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'stat' parameter of the stats command.
	 *
	 * @param type         The selected stat type
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getStatsStatChoices(String type, String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null || type == null || type.isBlank()) return List.of();

		String lowerValue = currentValue.toLowerCase();
		return provider.getStatNames(type).stream()
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

		Message message = event.getMessage();
		if (message.getType().isSystem()) {
			// Keep metadata for potential follow-up delete events, but don't bridge Discord system notices.
			cacheMessage(message);
			return;
		}
		if (event.isWebhookMessage()) {
			// Webhook messages are not bridged back to Minecraft to avoid loops,
			// but keep them cached so reply context can still be parsed consistently.
			cacheMessage(message);
			return;
		}

		// Build the main message line segments using DiscordMessageParser
		List<TextSegment> mainSegments = DiscordMessageParser.buildChatSegments(message);

		// Build reply segments if this is a reply to another message
		List<TextSegment> replySegments = DiscordMessageParser.buildReplySegments(message.getReferencedMessage());
		if (replySegments == null && message.getMessageReference() != null) {
			CachedMessage cachedRef = messageCache.get(message.getMessageReference().getMessageId());
			if (cachedRef != null) {
				replySegments = DiscordMessageParser.buildReplySegments(
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
		boolean isMentionEveryone = DiscordMessageParser.isMentionEveryone(message);
		if (mentionNotificationsEnabled) {
			Set<String> uuids = DiscordMessageParser.collectMentionedPlayerUuids(message);
			if (isMentionEveryone || !uuids.isEmpty()) {
				Member member = message.getMember();
				String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
				mentionNotificationText = DiscordMessageParser.getMentionNotificationText(effectiveName);
				mentionNotificationStyle = ConfigManager.getString("account_linking.mention_notifications.style", "title");
				mentionedPlayerUuids = new ArrayList<>(uuids);
			}
		}

		// Build and send the DiscordEventPacket to all connected clients
		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.CHAT, mainSegments);
		packet.replySegments = replySegments;
		packet.mentionNotificationText = mentionNotificationText;
		packet.mentionNotificationStyle = mentionNotificationStyle;
		packet.mentionedPlayerUuids = mentionedPlayerUuids;
		packet.mentionEveryone = isMentionEveryone;

		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);

		// Cache message for edit/delete reference
		cacheMessage(message);
	}

	@Override
	public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.reaction")) {
			return;
		}

		String configuredChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "in-game-chat");
		if (configuredChannel.isBlank()) {
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
		String roleColor = DiscordMessageParser.getRoleColorHex(member);

		EmojiUnion emoji = event.getEmoji();
		String emojiText = switch (emoji.getType()) {
			case UNICODE -> EmojiManager.replaceAllEmojis(emoji.getName(), e -> e.getDiscordAliases().getFirst());
			case CUSTOM -> ":" + emoji.getName() + ":";
		};

		event.retrieveMessage().queue(targetMessage -> {
			List<TextSegment> segments = DiscordMessageParser.buildReactionSegments(reactorName, roleColor, emojiText);
			DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.REACTION, segments);
			packet.replySegments = DiscordMessageParser.buildReplySegments(targetMessage);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		}, _ -> {
			List<TextSegment> segments = DiscordMessageParser.buildReactionSegments(reactorName, roleColor, emojiText);
			DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.REACTION, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
		});
	}

	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.edit")) {
			return;
		}

		String configuredChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "in-game-chat");
		if (configuredChannel.isBlank()) {
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
		String roleColor = DiscordMessageParser.getRoleColorHex(member);

		CachedMessage cached = messageCache.get(message.getId());
		if (cached != null && Objects.equals(cached.contentRaw(), message.getContentRaw())) {
			// Ignore metadata-only updates (e.g. pin/unpin) that do not change message text.
			return;
		}
		List<TextSegment> replySegments = null;
		if (cached != null && cached.contentRaw() != null) {
			replySegments = cached.replySegments();
			if (replySegments == null || replySegments.isEmpty()) {
				replySegments = DiscordMessageParser.buildReplySegments(
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
		List<TextSegment> editedMessageSegments = DiscordMessageParser.buildEditedMessageSegments(message);

		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.EDIT, notificationSegments);
		packet.replySegments = replySegments;
		packet.editedMessageSegments = editedMessageSegments;
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

		String configuredChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "in-game-chat");
		if (configuredChannel.isBlank()) {
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
			DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.DELETE, segments);
			logDiscordEventForConsole(packet);
			NetworkManager.broadcastToClients(packet);
			return;
		}

		List<TextSegment> segments = DiscordMessageParser.buildDeleteSegments(cached.authorName(), cached.authorRoleColor());
		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.DELETE, segments);
		packet.replySegments = DiscordMessageParser.buildReplySegments(
				cached.authorName(),
				cached.authorRoleColor(),
				null,
				cached.contentRaw()
		);
		if (cached.replySegments() != null && !cached.replySegments().isEmpty()) {
			packet.replySegments = cached.replySegments();
		}
		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);
	}

	/**
	 * Caches a message for later edit/delete reference.
	 */
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
		String roleColor = DiscordMessageParser.getRoleColorHex(member);
		List<TextSegment> replySegments = DiscordMessageParser.buildReplySegments(message);
		messageCache.put(message.getId(), new CachedMessage(name, roleColor, message.getContentRaw(), replySegments, message.getType().isSystem()));
	}

	/**
	 * Cached Discord message metadata for reply/edit/delete context rendering.
	 * <p>
	 * Stores the already-rendered one-line reply preview so fallback rendering keeps the
	 * same formatting pipeline (including webhook message formatting) without retaining full
	 * JDA {@link Message} objects in memory.
	 * {@code replySegments} may be null when a preview cannot be produced for a message.
	 *
	 * @param authorName      Cached effective name of the message author.
	 * @param authorRoleColor Cached role color of the message author.
	 * @param contentRaw      Cached raw content for fallback rebuilding.
	 * @param replySegments   Cached rendered reply preview, nullable.
	 * @param systemMessage   Whether this is a Discord system message (e.g. pin notice).
	 */
	private record CachedMessage(String authorName, String authorRoleColor, String contentRaw,
								 List<TextSegment> replySegments,
								 boolean systemMessage) {
	}
}
