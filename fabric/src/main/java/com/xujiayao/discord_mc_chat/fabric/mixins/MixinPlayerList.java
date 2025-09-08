package com.xujiayao.discord_mc_chat.fabric.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
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
		// PlayerJoin Event
		EventManager.dispatch(new MinecraftEvents.PlayerJoin(
				connection,
				serverPlayer,
				commonListenerCookie,
				ci
		));
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
		// PlayerQuit Event
		EventManager.dispatch(new MinecraftEvents.PlayerQuit(
				serverPlayer,
				ci
		));
	}
}
