package top.xujiayao.mcdiscordchat.mixins;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.events.PlayerDeathCallback;

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
