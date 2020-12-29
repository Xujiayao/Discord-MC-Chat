package io.gitee.xujiayao147.mcDiscordChatBridge.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

/**
 * @author Xujiayao
 */
@Mixin(ChatHud.class)
public class GetMessage {

	String msg;

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"))
	public void addMessage(Text text, int messageId, CallbackInfo info) {
		try {
			msg = text.getString();
			
			if (msg.contains("[Discord] "))
				return;
			
			if ((msg != null) && (!msg.equals(""))) {
				while (msg.startsWith(" ")) {
					msg = msg.substring(1);
				}
				
				if ((msg == null) || (msg.equals("")))
					return;
				
				if (msg.contains("Welcome!"))
					return;
				if (msg.contains("Welcome back to"))
					return;
				if (msg.contains("今天是服务器开服的第"))
					return;
				if (msg.contains("------------"))
					return;

				if (msg.contains("§")) {
					String[] strs = new String[msg.length()];

					for (int i = 0; i < strs.length; i++) {
						strs[i] = msg.substring(i, i + 1);
					}

					for (int i = 0; i < strs.length; i++) {
						if (strs[i].equals("§")) {
							strs[i] = "";
							strs[i + 1] = "";
						}
					}

					StringBuffer sb = new StringBuffer();
					for (String str : strs) {
						sb.append(str);
					}

					msg = sb.toString();
				}
				
				Main.getBot().sendMessage(msg);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
