package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
final class MixinMinecraftServer {

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getNanos()J", ordinal = 0))
	private void serverStarted(CallbackInfo ci) {
		// ServerStarted Event
		MinecraftEventHandler.onServerStarted(
				(MinecraftServer) (Object) this
		);
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"))
	private void serverStopping(CallbackInfo ci) {
		// ServerStopping Event
		MinecraftEventHandler.onServerStopping();
	}

	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerExit()V"))
	private void serverStopped(CallbackInfo ci) {
		// ServerStopped Event
		MinecraftEventHandler.onServerStopped();
	}

	/**
	 * ReloadResources Event: refresh DMCC's cached Minecraft translations after "&lt;command&gt; reload".
	 * <p>
	 * This hooks a real method instead of a synthetic lambda of {@code ReloadableServerResources}.
	 * That class is patched by NeoForge, which changes the shape of its synthetic lambdas, so a mixin
	 * targeting {@code lambda$loadResources$3} applies on Fabric but throws
	 * {@code InvalidInjectionException} on NeoForge. {@code MinecraftServer.reloadResources} exists with
	 * the same descriptor on both loaders, and waiting for the returned future means the refresh runs
	 * once the reload has actually finished.
	 *
	 * @param packsToEnable the packs being enabled, part of the target's signature
	 * @param cir           return callback carrying the reload future
	 */
	@Inject(method = "reloadResources", at = @At("RETURN"))
	private void resourcesReloaded(Collection<String> packsToEnable, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		CompletableFuture<Void> reload = cir.getReturnValue();

		if (reload != null) {
			reload.thenRun(MinecraftEventHandler::onReloadResources);
		}
	}
}
