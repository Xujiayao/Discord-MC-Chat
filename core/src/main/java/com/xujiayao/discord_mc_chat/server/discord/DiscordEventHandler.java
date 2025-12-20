package com.xujiayao.discord_mc_chat.server.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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

//		CommandListUpdateAction commandListUpdateAction = event.getJDA().updateCommands();
//
//		// Register common slash commands
//		commandListUpdateAction = commandListUpdateAction.addCommands(
//				Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")),
//				Commands.slash("info", I18nManager.getDmccTranslation("commands.info.description")),
//				Commands.slash("link", I18nManager.getDmccTranslation("commands.link.description"))
//						.addOption(OptionType.STRING, "code", I18nManager.getDmccTranslation("commands.link.params.code"), true),
//				Commands.slash("log", I18nManager.getDmccTranslation("commands.log.description"))
//						.addOption(OptionType.STRING, "file", I18nManager.getDmccTranslation("commands.log.params.file"), true),
//				Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")),
//				Commands.slash("stats", I18nManager.getDmccTranslation("commands.stats.description"))
//						.addOption(OptionType.STRING, "type", I18nManager.getDmccTranslation("commands.stats.params.type"), true)
//						.addOption(OptionType.STRING, "name", I18nManager.getDmccTranslation("commands.stats.params.name"), true),
//				Commands.slash("stop", I18nManager.getDmccTranslation("commands.stop.description")),
//				Commands.slash("update", I18nManager.getDmccTranslation("commands.update.description")),
//				Commands.slash("whitelist", I18nManager.getDmccTranslation("commands.whitelist.description"))
//						.addOption(OptionType.STRING, "player", I18nManager.getDmccTranslation("commands.whitelist.params.player"), true)
//		);
//
//		// Register Minecraft-only slash commands
//		commandListUpdateAction = commandListUpdateAction.addCommands(
//				Commands.slash("console", I18nManager.getDmccTranslation("commands.console.description"))
//						.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.console.params.command"), true)
//		);
//
//		// Register Standalone-only slash commands
//		commandListUpdateAction = commandListUpdateAction.addCommands(
//				Commands.slash("execute", I18nManager.getDmccTranslation("commands.execute.description"))
//						.addOption(OptionType.STRING, "server", I18nManager.getDmccTranslation("commands.execute.params.server"), true)
//						.addOption(OptionType.STRING, "command", I18nManager.getDmccTranslation("commands.execute.params.command"), true),
//				Commands.slash("start", I18nManager.getDmccTranslation("commands.start.description"))
//						.addOption(OptionType.STRING, "server", I18nManager.getDmccTranslation("commands.start.params.server"), true)
//		);
//
//		commandListUpdateAction.queue();

		// TODO: Initialize Rich Presence Manager
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		// TODO: Handle slash commands
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		// TODO: Handle incoming Discord messages and forward to Minecraft
	}
}
