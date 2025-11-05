package com.xujiayao.discord_mc_chat.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A container for all Minecraft-related event classes.
 *
 * @author Xujiayao
 */
public class MinecraftEvents {

	/**
	 * Posted when the server is started.
	 */
	public record ServerStarted(
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when the server is stopping.
	 */
	public record ServerStopping(
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when the server is stopped.
	 */
	public record ServerStopped(
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player joins the server.
	 */
	public record PlayerJoin(
			Connection connection,
			ServerPlayer serverPlayer,
			CommonListenerCookie commonListenerCookie,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player leaves the server.
	 */
	public record PlayerQuit(
			ServerPlayer serverPlayer,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player sends a chat message.
	 */
	public record PlayerChat(
			PlayerChatMessage playerChatMessage,
			ServerPlayer serverPlayer,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player executes a command.
	 */
	public record PlayerCommand(
			String command,
			ServerPlayer serverPlayer,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player dies.
	 */
	public record PlayerDie(
			DamageSource damageSource,
			ServerPlayer serverPlayer,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when a player makes an advancement.
	 */
	public record PlayerAdvancement(
			AdvancementHolder advancementHolder,
			String string,
			ServerPlayer player,
			AdvancementProgress advancementProgress,
			CallbackInfoReturnable<Boolean> cir
	) {
	}

	/**
	 * Posted when the /say command is used.
	 */
	public record SourceSay(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage,
			CallbackInfo ci
	) {
	}

	/**
	 * Posted when the /tellraw command is used.
	 */
	public record SourceTellRaw(
			CommandContext<CommandSourceStack> commandContext,
			CallbackInfoReturnable<Integer> cir
	) {
	}

	/**
	 * Posted when commands are being registered.
	 */
	public record CommandRegister(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CallbackInfo ci
	) {
	}
}
