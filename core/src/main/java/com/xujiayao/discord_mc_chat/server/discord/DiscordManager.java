package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;

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
			LOGGER.error("Discord bot token is not set in the config file!");
			return false;
		}

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

			LOGGER.info("Discord bot is ready. Logged in as tag: \"{}\"", jda.getSelfUser().getAsTag());

			jda.updateCommands()
					.addCommands(Commands.slash("help", I18nManager.getDmccTranslation("commands.help.description")))
					.addCommands(Commands.slash("reload", I18nManager.getDmccTranslation("commands.reload.description")))
					.addCommands(Commands.slash("shutdown", I18nManager.getDmccTranslation("commands.shutdown.description")))
					.queue();

			return true;
		} catch (Exception e) {
			LOGGER.error("Discord bot initialization was interrupted", e);
		}

		return false;
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
		}
	}
}
