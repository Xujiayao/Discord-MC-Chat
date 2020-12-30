package io.gitee.xujiayao147.mcDiscordChatBridge.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerAdvancementCallback;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * @author Xujiayao
 */
@Mixin(PlayerAdvancementTracker.class)
public class MixinPlayerAdvancementTracker {

	@Shadow
	private ServerPlayerEntity owner;

	@Inject(method = "grantCriterion", at = @At("RETURN"))
	private void grantCriterion(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		PlayerAdvancementCallback.EVENT.invoker().onPlayerAdvancement(owner, advancement);
	}
}
