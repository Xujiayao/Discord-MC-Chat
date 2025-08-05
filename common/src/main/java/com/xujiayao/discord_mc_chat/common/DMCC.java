package com.xujiayao.discord_mc_chat.common;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMCC {

	public static final Logger LOGGER = LoggerFactory.getLogger("discord_mc_chat");

	public static void main(String[] args) {
		LOGGER.info("Hello, World!");

		init("Standalone");
	}

	public static void init(String loader) {
		LOGGER.info("Initializing DMCC with loader: {}", loader);

		try {
			String token = ""; // intended to be empty
			JDA jda = JDABuilder.createDefault(token)
					.enableIntents(GatewayIntent.MESSAGE_CONTENT)
					.addEventListeners(new Discord())
					.build();
			jda.awaitReady();
			LOGGER.info("PingPongBot is ready!");
		} catch (Exception e) {
			LOGGER.error("Failed to initialize DMCC: {}", e.getMessage());
		} finally {
			LOGGER.info("DMCC initialization complete.");
		}
	}
}
