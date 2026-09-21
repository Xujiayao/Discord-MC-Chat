package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.DiscordRelayPacket;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

final class DiscordEventHandler extends ListenerAdapter {

	private static final int AUTOCOMPLETE_TIMEOUT_SECONDS = 5;
	private static final int MAX_CACHE_SIZE = 200;
	/**
	 * LRU cache ({@value #MAX_CACHE_SIZE} entries) of recent Discord messages, used to resolve replies, edits and
	 * deletes. Invalidation is explicit in {@link #onMessageDelete(MessageDeleteEvent)}, with no TTL (matching the
	 * previous behaviour); the map is synchronized because JDA event-pool and callback-pool threads both touch it.
	 */
	private static final Map<String, CachedMessage> messageCache = Collections.synchronizedMap(
			new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
					return size() > MAX_CACHE_SIZE;
				}
			});

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
		JdaCommandSender sender = new JdaCommandSender(event, opLevel);

		switch (name) {
			case "execute" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				CommandManager.execute(sender, name, at, command);
			}
			case "console" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				if (at != null) {
					// standalone mode: /console <at> <command>
					CommandManager.execute(sender, name, at, command);
				} else {
					// single_server mode: /console <command>
					CommandManager.execute(sender, name, command);
				}
			}
			case "log" -> {
				String file = event.getOption("file", OptionMapping::getAsString);
				CommandManager.execute(sender, name, file);
			}
			case "whitelist" -> {
				String player = event.getOption("player", OptionMapping::getAsString);
				CommandManager.execute(sender, name, player);
			}
			case "stats" -> {
				String type = event.getOption("type", OptionMapping::getAsString);
				String stat = event.getOption("stat", OptionMapping::getAsString);
				CommandManager.execute(sender, name, type, stat);
			}
			case "link" -> {
				String code = event.getOption("code", OptionMapping::getAsString);
				CommandManager.execute(sender, name, code);
			}
			default -> CommandManager.execute(sender, name);
		}

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

		int opLevel = OpLevelResolver.resolve(event.getMember(), event.getUser());
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

		event.replyChoices(choices).queue();
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
		return choices.stream().limit(25).toList();
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
				.toList();
	}

	private List<Command.Choice> getLogFileChoices(String currentValue) {
		List<String> logFiles = LogFileUtils.listLogFiles();
		String lowerValue = currentValue.toLowerCase();

		return logFiles.stream()
				.filter(f -> f.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(f -> new Command.Choice(f, f))
				.toList();
	}

	private List<Command.Choice> getStatsTypeChoices(String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null) return List.of();

		String normalizedValue = StatsCommand.normalizeMinecraftNamespace(currentValue);
		String lowerValue = normalizedValue == null ? "" : normalizedValue.toLowerCase();
		return provider.getStatTypes().stream()
				.filter(t -> t.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(t -> new Command.Choice(t, t))
				.toList();
	}

	private List<Command.Choice> getStatsStatChoices(String type, String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null || type == null || type.isBlank()) return List.of();

		String normalizedType = StatsCommand.normalizeMinecraftNamespace(type);
		String normalizedValue = StatsCommand.normalizeMinecraftNamespace(currentValue);
		String lowerValue = normalizedValue == null ? "" : normalizedValue.toLowerCase();
		return provider.getStatNames(normalizedType).stream()
				.filter(s -> s.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.toList();
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
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

		if (!ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat")) {
			return;
		}

		// Only handle messages from the configured in-game-chat channel (same as minecraft_to_discord player chat)
		String configuredChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "in-game-chat");
		if (configuredChannel.isBlank()) {
			return;
		}

		String channelId = event.getChannel().getId();
		String channelName = event.getChannel().getName();
		if (!channelId.equals(configuredChannel) && !channelName.equalsIgnoreCase(configuredChannel)) {
			return;
		}

		List<TextSegment> mainSegments = DiscordMessageParser.buildChatSegments(message);

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

		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.CHAT, mainSegments);
		packet.replySegments = replySegments;
		packet.mentionNotificationText = mentionNotificationText;
		packet.mentionNotificationStyle = mentionNotificationStyle;
		packet.mentionedPlayerUuids = mentionedPlayerUuids;
		packet.mentionEveryone = isMentionEveryone;

		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);

		cacheMessage(message);
	}

	private boolean tryExecuteConsoleMessage(MessageReceivedEvent event, Message message) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")
				|| !ConfigManager.getBoolean("console_forwarding.execute_messages_from_channel")) {
			return false;
		}

		String targetServer = DiscordManager.resolveConsoleTargetServer(event.getChannel().getId(), event.getChannel().getName());
		if (targetServer == null || targetServer.isBlank()) {
			return false;
		}

		String content = message.getContentRaw();
		if (content.isBlank()) {
			return false;
		}

		if ("standalone".equals(ModeManager.getMode())) {
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
		String roleColor = DiscordMessageParser.getRoleColorHex(member);

		EmojiUnion emoji = event.getEmoji();
		String emojiText = switch (emoji.getType()) {
			case UNICODE -> EmojiManager.replaceAllEmojis(emoji.getName(), e -> e.getDiscordAliases().getFirst());
			case CUSTOM -> ":" + emoji.getName() + ":";
		};

		event.retrieveMessage().queue(targetMessage -> broadcastReaction(reactorName, roleColor, emojiText, targetMessage),
				_ -> broadcastReaction(reactorName, roleColor, emojiText, null));
	}

	private static void broadcastReaction(String reactorName, String roleColor, String emojiText, Message targetMessage) {
		List<TextSegment> segments = DiscordMessageParser.buildReactionSegments(reactorName, roleColor, emojiText);
		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.REACTION, segments);
		if (targetMessage != null) {
			packet.replySegments = DiscordMessageParser.buildReplySegments(targetMessage);
		}
		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);
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
		if (event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
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

		List<TextSegment> notificationSegments = DiscordMessageParser.buildEditNotificationSegments(editorName, roleColor);

		List<TextSegment> editedMessageSegments = DiscordMessageParser.buildEditedMessageSegments(message);

		DiscordRelayPacket packet = new DiscordRelayPacket(DiscordRelayPacket.EventType.EDIT, notificationSegments);
		packet.replySegments = replySegments;
		packet.editedMessageSegments = editedMessageSegments;
		logDiscordEventForConsole(packet);
		NetworkManager.broadcastToClients(packet);

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
		Member member = message.getMember();
		String name = member != null ? member.getEffectiveName() : message.getAuthor().getName();
		String roleColor = DiscordMessageParser.getRoleColorHex(member);
		List<TextSegment> replySegments = DiscordMessageParser.buildReplySegments(message);
		messageCache.put(message.getId(), new CachedMessage(name, roleColor, message.getContentRaw(), replySegments, message.getType().isSystem()));
	}

	private record CachedMessage(String authorName, String authorRoleColor, String contentRaw,
	                             List<TextSegment> replySegments,
	                             boolean systemMessage) {
	}
}
