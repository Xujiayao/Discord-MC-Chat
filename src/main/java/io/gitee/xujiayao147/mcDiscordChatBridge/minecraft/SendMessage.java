package io.gitee.xujiayao147.mcDiscordChatBridge.minecraft;

import net.minecraft.client.MinecraftClient;

/**
 * @author Xujiayao
 */
public class SendMessage {

	MinecraftClient mc;

	public void sendMcMessage(String msg) {
		mc = MinecraftClient.getInstance();
		mc.player.sendChatMessage("[Discord] " + msg);
	}
	
	public String getPlayerName() {
		mc = MinecraftClient.getInstance();
		return mc.player.getEntityName();
	}
}
