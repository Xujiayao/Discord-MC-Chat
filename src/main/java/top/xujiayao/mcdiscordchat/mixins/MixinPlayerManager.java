package top.xujiayao.mcdiscordchat.mixins;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.events.PlayerJoinCallback;
import top.xujiayao.mcdiscordchat.events.PlayerLeaveCallback;

/**
 * @author Xujiayao
 */
@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		PlayerJoinCallback.EVENT.invoker().onJoin(connection, player);
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void remove(ServerPlayerEntity player, CallbackInfo ci) {
		PlayerLeaveCallback.EVENT.invoker().onLeave(player);
	}
}
