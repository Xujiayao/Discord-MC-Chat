package com.xujiayao.discord_mc_chat.fabric.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
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
public class MixinServerPlayer {

	@Inject(method = "die", at = @At("HEAD"))
	private void die(DamageSource damageSource, CallbackInfo ci) {
		// PlayerDie Event
		EventManager.dispatch(new MinecraftEvents.PlayerDie(
				damageSource,
				(ServerPlayer) (Object) this,
				ci
		));
	}
}
