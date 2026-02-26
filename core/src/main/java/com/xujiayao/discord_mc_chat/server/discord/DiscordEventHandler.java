package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
	 * <p>
	 * Resolution order (highest wins):
	 * 1. user_mappings: exact user ID or username match.
	 * 2. role_mappings: iterate user's roles, take the highest mapped OP level.
	 * 3. TODO: Account linking (linked MC account's actual OP level).
	 *
	 * @param member The Discord Member object (null if in DMs).
	 * @param user   The Discord User object.
	 * @return The resolved OP level (-1 to 4).
	 */
	private int getOpLevel(Member member, User user) {
		int maxOp = -1;

		// Check exact user mappings first (highest priority)
		JsonNode userMappings = ConfigManager.getConfigNode("account_linking.op_sync.user_mappings");
		if (userMappings.isArray()) {
			for (JsonNode node : userMappings) {
				if (user.getId().equals(node.path("user").asText()) || user.getName().equals(node.path("user").asText())) {
					maxOp = Math.max(maxOp, node.path("op_level").asInt(-1));
				}
			}
		}

		// Check role mappings if member exists (in a guild)
		if (member != null) {
			JsonNode roleMappings = ConfigManager.getConfigNode("account_linking.op_sync.role_mappings");
			if (roleMappings.isArray()) {
				for (Role role : member.getRoles()) {
					for (JsonNode node : roleMappings) {
						if (role.getId().equals(node.path("role").asText()) || role.getName().equals(node.path("role").asText())) {
							maxOp = Math.max(maxOp, node.path("op_level").asInt(-1));
						}
					}
				}
			}
		}

		// TODO: Account Linking logic
		// If maxOp is still -1, query links.json for linked Minecraft UUID
		// and fetch exact OP level from the bound MC account.

		return maxOp;
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		// TODO: Initialize Rich Presence Manager
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
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getExecuteCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		int opLevel = getOpLevel(event.getMember(), event.getUser());

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
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getConsoleCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		int opLevel = getOpLevel(event.getMember(), event.getUser());

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
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
