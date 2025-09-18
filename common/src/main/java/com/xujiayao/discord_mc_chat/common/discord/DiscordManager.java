package com.xujiayao.discord_mc_chat.common.discord;

import com.xujiayao.discord_mc_chat.common.utils.config.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.time.Duration;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

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
		String token = ConfigManager.getString("bot.token", "");
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

			LOGGER.info("Discord bot initialized successfully!");
			return true;
		} catch (Exception e) {
			LOGGER.error("Discord bot initialization was interrupted", e);
		}

		return false;
	}

	/**
	 * Shuts down the Discord bot.
	 */
	public static void shutdown() throws InterruptedException {
		if (jda != null) {
			jda.shutdown();

			if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
				if (!ConfigManager.getBoolean("shutdown.graceful_shutdown", true)) {
					jda.shutdownNow();
				}
			}

			LOGGER.info("Discord bot shutdown successfully!");
		}
	}
}
