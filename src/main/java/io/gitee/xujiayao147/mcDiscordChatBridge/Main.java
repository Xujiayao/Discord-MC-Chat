package io.gitee.xujiayao147.mcDiscordChatBridge;

import javax.security.auth.login.LoginException;

import io.gitee.xujiayao147.mcDiscordChatBridge.discord.DiscordBot;
import io.gitee.xujiayao147.mcDiscordChatBridge.minecraft.SendMessage;
import net.fabricmc.api.ModInitializer;

/**
 * @author Xujiayao
 */
public class Main implements ModInitializer {
	
	static SendMessage send = new SendMessage();
	static DiscordBot bot = new DiscordBot();

	@Override
	public void onInitialize() {
		try {
			bot.initialize();
		} catch (LoginException e) {
			e.printStackTrace();
		}

		System.out.println("MC Discord Chat Bridge is initialized.");
		System.out.println("服务器跨服聊天工具初始化完成。");
	}
	
	public static SendMessage getSend() {
		return send;
	}

	public static DiscordBot getBot() {
		return bot;
	}
}