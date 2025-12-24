package com.xujiayao.discord_mc_chat.server.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
