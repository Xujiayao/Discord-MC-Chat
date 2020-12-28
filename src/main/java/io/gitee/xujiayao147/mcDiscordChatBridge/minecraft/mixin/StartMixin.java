package io.gitee.xujiayao147.mcDiscordChatBridge.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.TitleScreen;

/**
 * @author Xujiayao
 */
@Mixin(TitleScreen.class)
public class StartMixin {

	@Inject(at = @At("HEAD"), method = "init()V")
	private void init(CallbackInfo info) {
		System.out.println("MC Discord Chat Bridge (Mixin) is initialized.");
		System.out.println("服务器跨服聊天工具 (Mixin) 初始化完成。");
	}
}
