package com.xujiayao.discord_mc_chat.common.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	@Override
	public void onReady(ReadyEvent event) {
		LOGGER.info("Discord bot is ready. Logged in as tag: \"{}\"", event.getJDA().getSelfUser().getAsTag());
		// TODO: Register slash commands
		// TODO: Initialize Rich Presence Manager
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		// TODO: Handle slash commands
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
