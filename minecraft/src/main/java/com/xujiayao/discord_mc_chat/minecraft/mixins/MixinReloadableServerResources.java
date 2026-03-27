package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
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

	@Inject(method = "lambda$loadResources$5", at = @At("RETURN"))
	private static void loadResources(ReloadableServerResources reloadableServerResources, Void ignore, CallbackInfoReturnable<ReloadableServerResources> cir) {
		// ReloadResources Event
		EventManager.post(new MinecraftEvents.ReloadResources());
	}
}
