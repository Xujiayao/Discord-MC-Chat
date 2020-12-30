package io.gitee.xujiayao147.mcDiscordChatBridge.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerDeathCallback;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayerEntity.class)
public class MixinServerPlayerEntity {

	@Inject(method = "onDeath", at = @At("HEAD"))
	private void onDeath(DamageSource source, CallbackInfo ci) {
		ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) (Object) this;
		PlayerDeathCallback.EVENT.invoker().onPlayerDeath(serverPlayerEntity, source);
	}
}
