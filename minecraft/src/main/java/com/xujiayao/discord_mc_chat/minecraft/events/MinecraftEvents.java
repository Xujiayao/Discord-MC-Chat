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

/**
 * A container for all Minecraft-related event classes.
 *
 * @author Xujiayao
 */
public final class MinecraftEvents {

	private MinecraftEvents() {
	}

	/**
	 * Posted when the server is started.
	 *
	 * @param minecraftServer The running Minecraft server instance.
	 */
	public record ServerStarted(
			MinecraftServer minecraftServer
	) {
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
	 *
	 * @param connection           The low-level network connection.
	 * @param serverPlayer         The joining player.
	 * @param commonListenerCookie Login metadata captured during handshake.
	 */
	public record PlayerJoin(
			Connection connection,
			ServerPlayer serverPlayer,
			CommonListenerCookie commonListenerCookie
	) {
	}

	/**
	 * Posted when a player leaves the server.
	 *
	 * @param serverPlayer The leaving player.
	 */
	public record PlayerQuit(
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player sends a chat message.
	 *
	 * @param playerChatMessage The signed chat message payload.
	 * @param serverPlayer      The speaking player.
	 */
	public record PlayerChat(
			PlayerChatMessage playerChatMessage,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player executes a command.
	 *
	 * @param command      The raw command string.
	 * @param serverPlayer The player executing the command.
	 */
	public record PlayerCommand(
			String command,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player dies.
	 *
	 * @param serverPlayer The player who died.
	 */
	public record PlayerDie(
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when a player makes an advancement.
	 *
	 * @param advancementHolder   The advancement definition.
	 * @param string              The advancement key or display text supplied by hook source.
	 * @param serverPlayer        The player who made progress.
	 * @param advancementProgress The advancement progress state.
	 */
	public record PlayerAdvancement(
			AdvancementHolder advancementHolder,
			String string,
			ServerPlayer serverPlayer,
			AdvancementProgress advancementProgress
	) {
	}

	/**
	 * Posted when a player changes game mode.
	 *
	 * @param gameType     The new game mode.
	 * @param serverPlayer The affected player.
	 */
	public record PlayerChangeGameMode(
			GameType gameType,
			ServerPlayer serverPlayer
	) {
	}

	/**
	 * Posted when the /say command is used.
	 *
	 * @param commandContext    The Brigadier command context.
	 * @param playerChatMessage The parsed chat message argument.
	 */
	public record SourceSay(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	/**
	 * Posted when the /tellraw command is used.
	 *
	 * @param commandContext The Brigadier command context.
	 * @param component      The resolved tellraw component.
	 */
	public record SourceTellRaw(
			CommandContext<CommandSourceStack> commandContext,
			Component component
	) {
	}

	/**
	 * Posted when the /msg, /tell, /w command is used.
	 *
	 * @param commandContext    The Brigadier command context.
	 * @param playerChatMessage The parsed private message payload.
	 */
	public record SourceMsg(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	/**
	 * Posted when the /me command is used.
	 *
	 * @param commandContext    The Brigadier command context.
	 * @param playerChatMessage The parsed emote message payload.
	 */
	public record SourceMe(
			CommandContext<CommandSourceStack> commandContext,
			PlayerChatMessage playerChatMessage
	) {
	}

	/**
	 * Posted when commands are being registered.
	 *
	 * @param dispatcher The command dispatcher to register into.
	 */
	public record CommandRegister(
			CommandDispatcher<CommandSourceStack> dispatcher
	) {
	}

	/**
	 * Posted when Minecraft is reloading resources.
	 */
	public record ReloadResources(
	) {
	}
}
