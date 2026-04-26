package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author Xujiayao
 */
@Mixin(GameModeCommand.class)
final class MixinGameModeCommand {

	@Inject(method = "setGameMode(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/GameType;)Z", at = @At("HEAD"))
	private static void setGameMode(CommandSourceStack source, ServerPlayer player, GameType type, CallbackInfoReturnable<Boolean> cir) {
		// PlayerChangeGameMode Event
		EventManager.post(new MinecraftEvents.PlayerChangeGameMode(
				type,
				player
		));
	}
}
