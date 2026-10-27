package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.authlib.GameProfile;
import com.xujiayao.discord_mc_chat.minecraft.linking.LoginGate;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(ServerLoginPacketListenerImpl.class)
final class MixinServerLoginPacketListenerImpl {

	@Shadow
	@Final
	private MinecraftServer server;

	@Shadow
	@Final
	private Connection connection;

	// Cancelling leaves the login in its verifying state, so vanilla calls this method again on the
	// next tick: that is what holds the player on "Logging in..." instead of dropping them
	@Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
	private void holdUnlinkedPlayer(GameProfile profile, CallbackInfo ci) {
		if (LoginGate.shouldHold(server, connection, (ServerLoginPacketListenerImpl) (Object) this, profile)) {
			ci.cancel();
		}
	}
}
