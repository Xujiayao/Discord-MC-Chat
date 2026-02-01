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
public class MixinReloadableServerResources {

	@Inject(method = "lambda$loadResources$2", at = @At("RETURN"))
	private static void loadResources(ReloadableServerResources reloadableserverresources, Void p_214306_, CallbackInfoReturnable<ReloadableServerResources> cir) {
		// ReloadResources Event
		EventManager.post(new MinecraftEvents.ReloadResources());
	}
}
