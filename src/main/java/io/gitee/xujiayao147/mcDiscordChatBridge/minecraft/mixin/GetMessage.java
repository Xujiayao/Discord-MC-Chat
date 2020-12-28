package io.gitee.xujiayao147.mcDiscordChatBridge.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import net.minecraft.client.gui.screen.Screen;

/**
 * @author Xujiayao
 */
@Mixin(Screen.class)
public class GetMessage {

	@Inject(at = @At("HEAD"), method = "sendMessage(Ljava/lang/String;Z)V", cancellable = true)
	private void onSendMessage(String text, boolean showInHistory, CallbackInfo info) {
		try {
			Main.getBot().sendMessage(text, Main.getSend().getPlayerName());

			Main.getSend().sendMcMessage("Message sent to Discord!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
