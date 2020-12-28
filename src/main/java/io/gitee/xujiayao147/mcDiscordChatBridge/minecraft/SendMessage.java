package io.gitee.xujiayao147.mcDiscordChatBridge.minecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;

/**
 * @author Xujiayao
 */
public class SendMessage {

	MinecraftClient mc;

	public void sendMcMessage(String msg) {
		mc = MinecraftClient.getInstance();
		mc.player.sendSystemMessage(new LiteralText("[Discord] " + msg), Util.NIL_UUID);
	}
	
	public String getPlayerName() {
		mc = MinecraftClient.getInstance();
		return mc.player.getEntityName();
	}
}
