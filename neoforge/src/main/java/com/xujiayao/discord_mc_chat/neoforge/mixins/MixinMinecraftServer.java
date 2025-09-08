package com.xujiayao.discord_mc_chat.neoforge.mixins;

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
	private void afterSetupServer(CallbackInfo ci) {
		// ServerStart Event
		LOGGER.info("[DMCC] Server started");
	}
}
