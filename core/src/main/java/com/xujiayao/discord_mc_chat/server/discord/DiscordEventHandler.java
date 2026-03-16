package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	private static final int AUTOCOMPLETE_TIMEOUT_SECONDS = 5;

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

			List<TextSegment> segments = DiscordMessageParser.buildCommandSegments(effectiveName, roleColor, name);
			DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.COMMAND, segments);
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
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		// Check if Discord-to-Minecraft chat is enabled
		boolean chatEnabled = ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat");
		if (!chatEnabled) {
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

		// Build the main message line segments using DiscordMessageParser
		List<TextSegment> mainSegments = DiscordMessageParser.buildChatSegments(message);

		// Build reply segments if this is a reply to another message
		boolean parseResponses = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.responses");
		List<TextSegment> replySegments = null;
		if (parseResponses) {
			Message referencedMessage = message.getReferencedMessage();
			replySegments = DiscordMessageParser.buildReplySegments(referencedMessage);
		}

		// Build mention notification data
		String mentionNotificationText = null;
		String mentionNotificationStyle = null;
		List<String> mentionedPlayerUuids = null;

		boolean mentionNotificationsEnabled = ConfigManager.getBoolean("account_linking.discord_mention_notifications.enable");
		if (mentionNotificationsEnabled) {
			Set<String> uuids = DiscordMessageParser.collectMentionedPlayerUuids(message);
			if (!uuids.isEmpty()) {
				Member member = message.getMember();
				String effectiveName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
				mentionNotificationText = DiscordMessageParser.getMentionNotificationText(effectiveName);
				mentionNotificationStyle = ConfigManager.getString("account_linking.discord_mention_notifications.style", "title");
				mentionedPlayerUuids = new ArrayList<>(uuids);
			}
		}

		// Build and send the DiscordEventPacket to all connected clients
		DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.CHAT, mainSegments);
		packet.replySegments = replySegments;
		packet.mentionNotificationText = mentionNotificationText;
		packet.mentionNotificationStyle = mentionNotificationStyle;
		packet.mentionedPlayerUuids = mentionedPlayerUuids;

		NetworkManager.broadcastToClients(packet);
	}
}
