package com.xujiayao.discord_mc_chat.utils.events;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordMessagePacket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Core events for communication between DMCC Core and Minecraft-specific implementations.
 * <p>
 * These events decouple the core command layer from any Minecraft-specific APIs.
 * Handlers for these events are registered in the Minecraft module (MinecraftEventHandler).
 *
 * @author Xujiayao
 */
public class CoreEvents {

	/**
	 * Posted when a Minecraft command needs to be executed on the local Minecraft server.
	 * <p>
	 * The handler should construct a virtual CommandSourceStack with the sender's OP level
	 * and dispatch the command to the Minecraft command dispatcher.
	 * <p>
	 * The handler MUST complete the {@code completionFuture} after the command has finished
	 * executing and all output has been sent to the sender. This enables reliable response
	 * timing for remote command execution (console/execute commands).
	 *
	 * @param sender           The command sender bridging the execution, used for replying results.
	 * @param commandLine      The raw Minecraft command line to be executed (without leading slash).
	 * @param completionFuture A future that the handler MUST complete when command execution is done.
	 *                         Complete with {@code null} on success, or exceptionally on failure.
	 */
	public record MinecraftCommandExecutionEvent(
			CommandSender sender,
			String commandLine,
			CompletableFuture<Void> completionFuture
	) {
	}

	/**
	 * Posted to gather auto-complete suggestions from the Minecraft command dispatcher.
	 * <p>
	 * The handler should parse the input against the Minecraft command dispatcher with the given
	 * OP level and append any matching suggestions to the mutable suggestions list.
	 *
	 * @param input       The current input string to auto-complete.
	 * @param opLevel     The OP level of the user requesting auto-complete.
	 * @param suggestions The mutable list of suggestions to append to.
	 */
	public record MinecraftCommandAutoCompleteEvent(
			String input,
			int opLevel,
			List<String> suggestions
	) {
	}

	/**
	 * Posted when a Minecraft player needs a verification code for account linking.
	 * <p>
	 * The handler should resolve the player's UUID and name from the Minecraft server
	 * and invoke the callback with the result.
	 *
	 * @param playerUuid The UUID of the Minecraft player (as string).
	 * @param playerName The display name of the Minecraft player.
	 * @param callback   A consumer that receives the generated verification code,
	 *                   or null if the player is already linked.
	 */
	public record LinkCodeRequestEvent(
			String playerUuid,
			String playerName,
			Consumer<String> callback
	) {
	}

	/**
	 * Posted when a Minecraft player wants to unlink their account.
	 * <p>
	 * The handler should remove the link for the given UUID and invoke the callback.
	 *
	 * @param playerUuid The UUID of the Minecraft player (as string).
	 * @param callback   A consumer that receives true if unlink was successful, false otherwise.
	 */
	public record UnlinkByUuidEvent(
			String playerUuid,
			Consumer<Boolean> callback
	) {
	}

	/**
	 * Posted by the Client when receiving a link code response from the Server.
	 * <p>
	 * The Minecraft module should notify the player with the verification code.
	 *
	 * @param playerUuid    The UUID of the Minecraft player.
	 * @param code          The verification code, or null if already linked.
	 * @param alreadyLinked Whether the player is already linked.
	 * @param discordName   The Discord username if already linked (for display), or empty string.
	 */
	public record LinkCodeResponseEvent(
			String playerUuid,
			String code,
			boolean alreadyLinked,
			String discordName
	) {
	}

	/**
	 * Posted by the Client when receiving an unlink response from the Server.
	 * <p>
	 * The Minecraft module should notify the player with the result.
	 *
	 * @param playerUuid  The UUID of the Minecraft player.
	 * @param success     Whether the unlink was successful.
	 * @param discordName The Discord username that was unlinked from (for display), or empty string.
	 */
	public record UnlinkResponseEvent(
			String playerUuid,
			boolean success,
			String discordName
	) {
	}

	/**
	 * Posted when OP levels should be synchronized to the Minecraft server.
	 * <p>
	 * This is a full-reset sync: the handler should clear all existing OP assignments
	 * and reapply the provided mapping. Players not in the map should be de-opped (OP 0).
	 *
	 * @param opLevels A map of Minecraft UUID (as string) to the desired OP level (0-4).
	 */
	public record OpSyncEvent(
			Map<String, Integer> opLevels
	) {
	}

	/**
	 * Posted by the Client when receiving a Discord chat/command message from the Server.
	 * <p>
	 * The Minecraft module should build Components from the text parts and broadcast
	 * to all online players. It should also handle mention notifications if applicable.
	 *
	 * @param packet The Discord message packet containing pre-formatted text parts.
	 */
	public record DiscordChatMessageEvent(
			DiscordMessagePacket packet
	) {
	}
}
