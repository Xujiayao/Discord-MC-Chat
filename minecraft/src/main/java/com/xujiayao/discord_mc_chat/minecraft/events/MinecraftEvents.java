package com.xujiayao.discord_mc_chat.minecraft.events;

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

/**
 * A container for all Minecraft-related event classes.
 *
 * @author Xujiayao
 */
public class MinecraftEvents {

	/**
	 * Posted when the server is started.
	 */
	public record ServerStarted() {
	}

	/**
	 * Posted when the server is stopping.
	 */
	public record ServerStopping() {
	}

	/**
	 * Posted when the server is stopped.
	 */
	public record ServerStopped() {
	}

	/**
	 * Posted when a player joins the server.
	 */
	public record PlayerJoin(
			Connection connection,
			ServerPlayer serverPlayer,
			CommonListenerCookie commonListenerCookie
	) {
	}

	/**
	 * Posted when a player leaves the server.
	 */
	public record PlayerQuit(
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player sends a chat message.
	 */
	public record PlayerChat(
			PlayerChatMessage playerChatMessage,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player executes a command.
	 */
	public record PlayerCommand(
			String command,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player dies.
	 */
	public record PlayerDie(
			DamageSource damageSource,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player makes an advancement.
	 */
	public record PlayerAdvancement(
			AdvancementHolder advancementHolder,
			String string,
			ServerPlayer player,
			AdvancementProgress advancementProgress
	) {
	}

	/**
	 * Posted when the /say command is used.
	 */
	public record SourceSay(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	/**
	 * Posted when the /tellraw command is used.
	 */
	public record SourceTellRaw(
			CommandContext<CommandSourceStack> commandContext
	) {
	}

	/**
	 * Posted when commands are being registered.
	 */
	public record CommandRegister(
			CommandDispatcher<CommandSourceStack> dispatcher
	) {
	}
}
