package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket.TextSegment;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

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

		// Broadcast command notification to Minecraft if enabled
		Boolean broadcastCommand = ConfigManager.getBoolean("broadcasts.discord_to_minecraft.command");
		if (broadcastCommand != null && broadcastCommand) {
			String effectiveName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
			String roleColor = DiscordMessageParser.getMemberRoleColor(event.getMember());
			DiscordEventPacket commandPacket = new DiscordEventPacket(effectiveName, roleColor, name);
			NetworkManager.broadcastToClients(commandPacket);
		}

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

		// Ignore messages from non-guild channels (e.g. DMs)
		if (!event.isFromGuild()) {
			return;
		}

		String channelName = event.getChannel().getName();

		// Check if this is a console channel message
		if (isConsoleChannel(channelName)) {
			handleConsoleChannelMessage(event);
			return;
		}

		// Check if this is a chat channel message
		if (isChatChannel(channelName)) {
			handleChatChannelMessage(event);
		}
	}

	/**
	 * Handles a message received in a console channel.
	 * <p>
	 * If {@code console_forwarding.execute_messages_from_channel} is enabled,
	 * the message content is dispatched as a Minecraft command to the appropriate client.
	 *
	 * @param event The message received event.
	 */
	private void handleConsoleChannelMessage(MessageReceivedEvent event) {
		Boolean executeEnabled = ConfigManager.getBoolean("console_forwarding.execute_messages_from_channel");
		if (executeEnabled == null || !executeEnabled) {
			return;
		}

		String commandLine = event.getMessage().getContentRaw().trim();
		if (commandLine.isEmpty()) {
			return;
		}

		// Strip leading slash if present
		if (commandLine.startsWith("/")) {
			commandLine = commandLine.substring(1);
		}

		int opLevel = getOpLevel(event.getMember(), event.getAuthor());
		String requestId = UUID.randomUUID().toString();

		String mode = ModeManager.getMode();
		if ("standalone".equals(mode)) {
			// Find the target server for this console channel
			String targetServer = getConsoleChannelServer(event.getChannel().getName());
			if (targetServer != null) {
				NetworkManager.sendPacketToClient(
						new ConsoleRequestPacket(requestId, opLevel, commandLine), targetServer);
			}
		} else {
			// single_server mode: send to the Internal client
			NetworkManager.broadcastToClients(new ConsoleRequestPacket(requestId, opLevel, commandLine));
		}
	}

	/**
	 * Handles a message received in a chat channel.
	 * <p>
	 * Parses the message content into rich text segments and broadcasts a
	 * {@link DiscordEventPacket} to all connected Minecraft clients.
	 *
	 * @param event The message received event.
	 */
	private void handleChatChannelMessage(MessageReceivedEvent event) {
		Boolean chatEnabled = ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat");
		if (chatEnabled == null || !chatEnabled) {
			return;
		}

		Message message = event.getMessage();
		Member member = event.getMember();
		String effectiveName = member != null ? member.getEffectiveName() : event.getAuthor().getName();
		String roleColor = DiscordMessageParser.getMemberRoleColor(member);

		// Parse the message content into text segments
		List<TextSegment> segments = DiscordMessageParser.parse(message);
		if (segments.isEmpty()) {
			return;
		}

		// Handle reply context
		boolean hasReply = false;
		String replyEffectiveName = null;
		String replyRoleColor = null;
		List<TextSegment> replySegments = null;

		Boolean replyContextEnabled = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.reply_context");
		if (replyContextEnabled == null || replyContextEnabled) {
			Message referencedMessage = message.getReferencedMessage();
			if (referencedMessage != null) {
				hasReply = true;
				Member replyMember = referencedMessage.getMember();
				replyEffectiveName = replyMember != null ? replyMember.getEffectiveName() : referencedMessage.getAuthor().getName();
				replyRoleColor = DiscordMessageParser.getMemberRoleColor(replyMember);
				replySegments = DiscordMessageParser.parse(referencedMessage);
			}
		}

		// Collect Minecraft UUIDs for mentioned users (for in-game mention notifications)
		List<String> mentionedMinecraftUuids = new ArrayList<>();
		for (User mentionedUser : message.getMentions().getUsers()) {
			List<String> uuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(mentionedUser.getId());
			mentionedMinecraftUuids.addAll(uuids);
		}
		// Also check mentioned roles for players linked to users with those roles
		for (net.dv8tion.jda.api.entities.Role mentionedRole : message.getMentions().getRoles()) {
			if (event.getGuild() != null) {
				for (Member m : event.getGuild().getMembersWithRoles(mentionedRole)) {
					List<String> uuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(m.getId());
					mentionedMinecraftUuids.addAll(uuids);
				}
			}
		}

		// Build and broadcast the Discord event packet
		DiscordEventPacket packet = new DiscordEventPacket(
				effectiveName, roleColor, segments,
				hasReply, replyEffectiveName, replyRoleColor, replySegments,
				mentionedMinecraftUuids
		);

		NetworkManager.broadcastToClients(packet);
	}

	/**
	 * Checks if the given channel name matches a configured chat channel.
	 * <p>
	 * The chat channel is the one defined in {@code broadcasts.minecraft_to_discord.player.chat}.
	 *
	 * @param channelName The Discord channel name to check.
	 * @return true if it is a chat channel.
	 */
	private boolean isChatChannel(String channelName) {
		String chatChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat");
		return chatChannel != null && !chatChannel.isEmpty() && chatChannel.equalsIgnoreCase(channelName);
	}

	/**
	 * Checks if the given channel name matches a configured console channel.
	 *
	 * @param channelName The Discord channel name to check.
	 * @return true if it is a console channel.
	 */
	private boolean isConsoleChannel(String channelName) {
		Boolean consoleEnabled = ConfigManager.getBoolean("console_forwarding.enable");
		if (consoleEnabled == null || !consoleEnabled) {
			return false;
		}

		String mode = ModeManager.getMode();
		if ("standalone".equals(mode)) {
			// Standalone mode: check all console channels
			JsonNode channelsNode = ConfigManager.getConfigNode("console_forwarding.channels");
			if (channelsNode != null && channelsNode.isArray()) {
				for (JsonNode entry : channelsNode) {
					String channel = entry.path("channel").asText("");
					if (channel.equalsIgnoreCase(channelName)) {
						return true;
					}
				}
			}
		} else {
			// Single server mode: check the single console channel
			String consoleChannel = ConfigManager.getString("console_forwarding.channel");
			return consoleChannel != null && consoleChannel.equalsIgnoreCase(channelName);
		}
		return false;
	}

	/**
	 * Gets the target server name for a console channel in standalone mode.
	 *
	 * @param channelName The Discord channel name.
	 * @return The server name, or null if not found.
	 */
	private String getConsoleChannelServer(String channelName) {
		JsonNode channelsNode = ConfigManager.getConfigNode("console_forwarding.channels");
		if (channelsNode != null && channelsNode.isArray()) {
			for (JsonNode entry : channelsNode) {
				String channel = entry.path("channel").asText("");
				if (channel.equalsIgnoreCase(channelName)) {
					return entry.path("server").asText(null);
				}
			}
		}
		return null;
	}
}
