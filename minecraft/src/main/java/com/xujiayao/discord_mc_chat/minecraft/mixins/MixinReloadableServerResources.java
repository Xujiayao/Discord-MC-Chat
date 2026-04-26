package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author Xujiayao
 */
@Mixin(ReloadableServerResources.class)
final class MixinReloadableServerResources {

	@Inject(method = "lambda$loadResources$3", at = @At("RETURN"))
	private static void lambda$loadResources$3(ReloadableServerResources result, Object ignore, CallbackInfoReturnable<ReloadableServerResources> cir) {
		// ReloadResources Event
		EventManager.post(new MinecraftEvents.ReloadResources());
	}
}
