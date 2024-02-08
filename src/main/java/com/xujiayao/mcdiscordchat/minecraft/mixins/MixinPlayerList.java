package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
public class MixinPlayerList {

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
		MinecraftEvents.PLAYER_JOIN.invoker().join(serverPlayer);
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
		MinecraftEvents.PLAYER_QUIT.invoker().quit(serverPlayer);
	}
}
