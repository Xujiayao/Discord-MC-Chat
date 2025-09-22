package com.xujiayao.discord_mc_chat.common.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	@Override
	public void onReady(@Nonnull ReadyEvent event) {
		LOGGER.info("Discord bot is ready. Logged in as tag: \"{}\"", event.getJDA().getSelfUser().getAsTag());

		CommandListUpdateAction commandListUpdateAction = event.getJDA().updateCommands();

		// Register common slash commands
		commandListUpdateAction = commandListUpdateAction.addCommands(
				Commands.slash("help", "Show a list of available commands"),
				Commands.slash("info", "Show server status"),
				Commands.slash("link", "Link your Discord account with your Minecraft account")
						.addOption(OptionType.STRING, "code", "The link code you received in-game", true),
				Commands.slash("log", "View a log file")
						.addOption(OptionType.STRING, "file", "The log file to view", true),
				Commands.slash("reload", "Reload DMCC"),
				Commands.slash("stats", "Show specific statistics leaderboard")
						.addOption(OptionType.STRING, "type", "The type of statistics", true)
						.addOption(OptionType.STRING, "name", "The name of the statistics", true),
				Commands.slash("stop", "Stop DMCC"),
				Commands.slash("update", "Check for DMCC updates"),
				Commands.slash("whitelist", "Add a player to the server whitelist")
						.addOption(OptionType.STRING, "player", "The player's Minecraft username", true)
		);

		// Register Minecraft-only slash commands
		commandListUpdateAction = commandListUpdateAction.addCommands(
				Commands.slash("console", "Execute a Minecraft command on the server")
						.addOption(OptionType.STRING, "command", "The command to execute", true)
		);

		// Register Standalone-only slash commands
		commandListUpdateAction = commandListUpdateAction.addCommands(
				Commands.slash("execute", "Execute a DMCC command on a sub-server")
						.addOption(OptionType.STRING, "server", "The name of the server", true)
						.addOption(OptionType.STRING, "command", "The DMCC command to execute", true),
				Commands.slash("start", "Start a sub-server")
						.addOption(OptionType.STRING, "server", "The name of the server to start", true)
		);

		commandListUpdateAction.queue();

		// TODO: Initialize Rich Presence Manager
	}

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		// TODO: Handle slash commands
	}

	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
