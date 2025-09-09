package com.xujiayao.discord_mc_chat.fabric.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J", ordinal = 0))
	private void serverStarted(CallbackInfo ci) {
		// ServerStarted Event
		LOGGER.info("[DMCC] Server started");
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"))
	private void serverStopping(CallbackInfo ci) {
		// ServerStopping Event
		LOGGER.info("[DMCC] Server stopping");
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerExit()V"))
	private void serverStopped(CallbackInfo ci) {
		// ServerStopped Event
		LOGGER.info("[DMCC] Server stopped");
	}
}
