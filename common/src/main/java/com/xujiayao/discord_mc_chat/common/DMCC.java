package com.xujiayao.discord_mc_chat.common;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class DMCC {

	public static void main(String[] args) {
		System.out.println("Hello, World!");
	}

	public static void init(String loader) {
		System.out.println("Initializing DMCC with loader: " + loader);

		try {
			// TOKEN should be replaced with your Discord bot token
			String token = "";
			JDA jda = JDABuilder.createDefault(token)
					.addEventListeners(new Discord())
					.build();
			jda.awaitReady();
			System.out.println("PingPongBot is ready!");
		} catch (Exception e) {
			System.err.println("Failed to initialize DMCC: " + e.getMessage());
			e.printStackTrace();
		} finally {
			System.out.println("DMCC initialization complete.");
		}
	}
}
