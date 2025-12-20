package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.commands.CommandEventHandler;
import com.xujiayao.discord_mc_chat.commands.CommandEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		LOGGER.info("Discord bot is ready. Logged in as tag: \"{}\"", event.getJDA().getSelfUser().getAsTag());

		event.getJDA().updateCommands()
				.addCommands(Commands.slash("disable", I18nManager.getDmccTranslation("commands.disable.description")))
				.addCommands(Commands.slash("enable", I18nManager.getDmccTranslation("commands.enable.description")))
				.addCommands(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")))
				.addCommands(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")))
				.addCommands(Commands.slash("shutdown", I18nManager.getDmccTranslation("commands.shutdown.description")))
				.queue();

		// TODO: Initialize Rich Presence Manager
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		event.deferReply().queue();

		switch (event.getName()) {
			case "disable" -> {
				event.getHook().editOriginal(I18nManager.getDmccTranslation("commands.disable.disabling")).queue();
				EventManager.post(new CommandEvents.DisableEvent());
			}
			case "enable" -> {
				event.getHook().editOriginal(I18nManager.getDmccTranslation("commands.enable.enabling")).queue();
				EventManager.post(new CommandEvents.EnableEvent());
			}
			case "help" -> event.getHook().editOriginal(CommandEventHandler.buildHelpMessage()).queue();
			case "reload" -> {
				event.getHook().editOriginal(I18nManager.getDmccTranslation("commands.reload.reloading")).queue();
				EventManager.post(new CommandEvents.ReloadEvent());
			}
			case "shutdown" -> {
				event.getHook().editOriginal(I18nManager.getDmccTranslation("commands.shutdown.shutting_down")).queue();
				EventManager.post(new CommandEvents.ShutdownEvent());
			}
		}
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
