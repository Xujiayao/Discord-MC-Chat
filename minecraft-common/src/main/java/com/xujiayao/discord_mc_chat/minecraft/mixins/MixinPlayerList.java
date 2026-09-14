package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
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
final class MixinPlayerList {

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	private void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
		// PlayerJoin Event
		MinecraftEventHandler.onPlayerJoin(
				player
		);
	}

	@Inject(method = "remove", at = @At("RETURN"))
	private void remove(ServerPlayer player, CallbackInfo ci) {
		// PlayerQuit Event
		MinecraftEventHandler.onPlayerQuit(
				player
		);
	}
}
