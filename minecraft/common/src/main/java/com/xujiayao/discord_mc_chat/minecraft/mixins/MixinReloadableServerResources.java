package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * @author Xujiayao
 */
@Mixin(ReloadableServerResources.class)
final class MixinReloadableServerResources {

	// The event is posted when the future returned by loadResources completes, instead of inside the
	// synthetic "lambda$loadResources$N" method doing the same thing. NeoForge recompiles the patched
	// Minecraft sources, which renumbers those lambdas, so their names are not stable across loaders.
	@Inject(method = "loadResources", at = @At("RETURN"))
	private static void loadResources(CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
		// ReloadResources Event
		cir.getReturnValue().whenComplete((resources, throwable) -> EventManager.post(new MinecraftEvents.ReloadResources()));
	}
}
