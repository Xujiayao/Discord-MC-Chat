package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		// TODO: Initialize Rich Presence Manager
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		event.deferReply().queue();

		String name = event.getName();
		switch (name) {
			case "execute" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event), name, at, command);
			}
			case "log" -> {
				String file = event.getOption("file", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event), name, file);
			}
			default -> CommandManager.execute(new JdaCommandSender(event), name);
		}
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
