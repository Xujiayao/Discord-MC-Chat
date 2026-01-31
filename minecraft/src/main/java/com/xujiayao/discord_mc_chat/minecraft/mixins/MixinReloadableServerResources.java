package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author Xujiayao
 */
@Mixin(ReloadableServerResources.class)
public class MixinReloadableServerResources {

	@Inject(method = "loadResources", at = @At("RETURN"))
	private static void loadResources(ResourceManager p_248588_,
									  LayeredRegistryAccess<RegistryLayer> p_335667_,
									  List<Registry.PendingTags<?>> p_363739_,
									  FeatureFlagSet p_250212_,
									  Commands.CommandSelection p_249301_,
									  int p_251126_,
									  Executor p_249136_,
									  Executor p_249601_,
									  CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
		// ReloadResources Event
		EventManager.post(new MinecraftEvents.ReloadResources());
	}
}
