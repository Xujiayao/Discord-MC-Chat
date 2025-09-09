package com.xujiayao.discord_mc_chat.neoforge.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J", ordinal = 0))
	private void serverStarted(CallbackInfo ci) {
		// ServerStarted Event
		EventManager.post(new MinecraftEvents.ServerStarted(
				ci
		));
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"))
	private void serverStopping(CallbackInfo ci) {
		// ServerStopping Event
		EventManager.post(new MinecraftEvents.ServerStopping(
				ci
		));
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerExit()V"))
	private void serverStopped(CallbackInfo ci) {
		// ServerStopped Event
		EventManager.post(new MinecraftEvents.ServerStopped(
				ci
		));
	}
}
