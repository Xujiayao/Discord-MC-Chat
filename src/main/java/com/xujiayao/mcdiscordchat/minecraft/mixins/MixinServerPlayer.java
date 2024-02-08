package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
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
		MinecraftEvents.PLAYER_DIE.invoker().die((ServerPlayer) (Object) this, damageSource);
	}
}
