package com.xujiayao.discord_mc_chat.minecraft.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

public final class MinecraftEvents {

	private MinecraftEvents() {
	}

	public record ServerStarted(
			MinecraftServer minecraftServer
	) {
	}

	public record ServerStopping() {
	}

	public record ServerStopped() {
	}

	public record PlayerJoin(
			Connection connection,
			ServerPlayer serverPlayer,
			CommonListenerCookie commonListenerCookie
	) {
	}

	public record PlayerQuit(
			ServerPlayer serverPlayer
	) {
	}

	public record PlayerChat(
			PlayerChatMessage playerChatMessage,
			ServerPlayer serverPlayer
	) {
	}

	public record PlayerCommand(
			String command,
			ServerPlayer serverPlayer
	) {
	}

	public record PlayerDie(
			ServerPlayer serverPlayer
	) {
	}

	public record PlayerAdvancement(
			AdvancementHolder advancementHolder,
			String string,
			ServerPlayer serverPlayer,
			AdvancementProgress advancementProgress
	) {
	}

	public record PlayerChangeGameMode(
			GameType gameType,
			ServerPlayer serverPlayer
	) {
	}

	public record SourceSay(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	public record SourceTellRaw(
			CommandContext<CommandSourceStack> commandContext,
			Component component
	) {
	}

	public record SourceMsg(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	public record SourceMe(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	public record CommandRegister(
			CommandDispatcher<CommandSourceStack> dispatcher
	) {
	}

	public record ReloadResources(
	) {
	}
}
