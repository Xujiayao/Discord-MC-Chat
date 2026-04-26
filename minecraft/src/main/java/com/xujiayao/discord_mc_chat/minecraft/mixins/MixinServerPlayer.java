package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayer.class)
final class MixinServerPlayer {

	@Inject(method = "die", at = @At("HEAD"))
	private void die(DamageSource source, CallbackInfo ci) {
		// PlayerDie Event
		EventManager.post(new MinecraftEvents.PlayerDie(
				(ServerPlayer) (Object) this
		));
	}
}
