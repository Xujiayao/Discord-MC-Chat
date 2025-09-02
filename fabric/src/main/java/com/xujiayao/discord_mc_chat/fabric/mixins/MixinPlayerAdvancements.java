package com.xujiayao.discord_mc_chat.fabric.mixins;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(PlayerAdvancements.class)
public class MixinPlayerAdvancements {

	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V", shift = At.Shift.AFTER))
	private void award(AdvancementHolder advancementHolder, String string, CallbackInfoReturnable<Boolean> cir) {
		// PlayerAdvancement Event
		LOGGER.info("Player {} has made the advancement {}", player.getName().getString(), advancementHolder.value().name());
	}
}
