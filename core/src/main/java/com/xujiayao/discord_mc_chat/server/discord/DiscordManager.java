package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages Discord using JDA (Java Discord API).
 *
 * @author Xujiayao
 */
public class DiscordManager {

	private static JDA jda;

	/**
	 * Initializes the Discord bot.
	 *
	 * @return true if initialization is successful, false otherwise.
	 */
	public static boolean init() {
		String token = ConfigManager.getString("discord.bot.token");
		if (token.isBlank()) {
			LOGGER.error(I18nManager.getDmccTranslation("discord.manager.token_missing"));
			return false;
		}

		// Use a custom executor with our special ThreadFactory to ensure ClassLoader is correct
		try (ExecutorService executor = Executors.newCachedThreadPool(ExecutorServiceUtils.newThreadFactory("DMCC-DiscordInit"))) {
			try {
				// Blocks until JDA is ready
				CompletableFuture<Void> readyFuture = CompletableFuture.runAsync(() -> {
					try {
						jda = JDABuilder.createDefault(token)
								.enableIntents(
										GatewayIntent.MESSAGE_CONTENT,
										GatewayIntent.GUILD_MEMBERS
								)
								.setMemberCachePolicy(MemberCachePolicy.ALL)
								.addEventListeners(new DiscordEventHandler())
								.build();

						jda.awaitReady();
					} catch (InterruptedException e) {
						LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
					}
				}, executor);

				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!readyFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.waiting_ready"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				readyFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.ready", jda.getSelfUser().getAsTag()));
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.init_interrupted"), e);
			}

			if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
				// Don't forget to shut down the temporary executor
				executor.shutdown();
				return false;
			}

			// Blocks until commands are updated
			try {
				List<CommandData> commands = new ArrayList<>();
				commands.add(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")));
				commands.add(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")));
				if ("standalone".equals(ModeManager.getMode())) {
					commands.add(Commands.slash("shutdown", I18nManager.getDmccTranslation("commands.shutdown.description")));
				}

				CompletableFuture<List<Command>> updateFuture = jda.updateCommands().addCommands(commands).submit();
				CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
					if (!updateFuture.isDone()) {
						LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.registering_commands"));
					}
				}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, executor));

				updateFuture.join();
				checkFuture.cancel(false);

				LOGGER.info(I18nManager.getDmccTranslation("discord.manager.commands_success"));
				return true;
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("discord.manager.commands_failed"), e);
			}
		}

		return false;
	}

	/**
	 * Sends a message to a Discord channel.
	 *
	 * @param channelIdentifier The ID or Name of the channel.
	 * @param content           The message content.
	 */
	public static void sendMessage(String channelIdentifier, String content) {
		TextChannel channel = getTextChannel(channelIdentifier);
		if (channel != null) {
			channel.sendMessage(content).queue();
		}
	}

	/**
	 * Retrieves a TextChannel by its ID or name.
	 *
	 * @param identifier The ID or name of the channel.
	 * @return The TextChannel if found, null otherwise.
	 */
	private static TextChannel getTextChannel(String identifier) {
		TextChannel tc;

		// Try search by name
		// Return first result. Use with caution if multiple channels have the same name.
		List<TextChannel> channels = jda.getTextChannelsByName(identifier, true);
		if (!channels.isEmpty()) {
			tc = channels.getFirst();
		} else {
			// Try parsing as ID
			tc = jda.getTextChannelById(identifier);
			if (tc == null) {
				LOGGER.error("discord.manager.channel_not_found", identifier);
				return null;
			}
		}

		if (!tc.canTalk()) {
			LOGGER.error("discord.manager.channel_cannot_talk", identifier);
			return null;
		}

		return tc;
	}

	/**
	 * Shuts down the Discord bot.
	 */
	public static void shutdown() {
		if (jda != null) {
			jda.shutdown();
			try {
				if (ConfigManager.getBoolean("shutdown.graceful_shutdown")) {
					// Allow up to 10 minutes for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofMinutes(10));
				} else {
					// Allow up to 5 seconds for ongoing requests to complete
					boolean ignored = jda.awaitShutdown(Duration.ofSeconds(5));
				}
			} catch (Exception ignored) {
			}
			jda.shutdownNow();

			jda = null;
		}
	}
}
