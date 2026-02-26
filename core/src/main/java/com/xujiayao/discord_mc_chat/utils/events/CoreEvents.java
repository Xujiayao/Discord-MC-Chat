package com.xujiayao.discord_mc_chat.utils.events;

import com.xujiayao.discord_mc_chat.commands.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
}
