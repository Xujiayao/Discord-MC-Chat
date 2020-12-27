package main;

import javax.security.auth.login.LoginException;

import discord.Bot;
import net.fabricmc.api.ModInitializer;

/**
 * @author Xujiayao
 */
public class Main implements ModInitializer {
	
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		
		System.out.println("服务器跨服聊天机器人已启动！");
		
		try {
			Bot.initialize();
		} catch (LoginException e) {
			e.printStackTrace();
		}
	}
}
