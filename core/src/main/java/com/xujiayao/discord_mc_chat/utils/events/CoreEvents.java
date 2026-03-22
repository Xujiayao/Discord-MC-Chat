package com.xujiayao.discord_mc_chat.utils.events;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core events for communication between DMCC Core and Minecraft-specific implementations.
 * <p>
 * These events decouple the core command layer from any Minecraft-specific APIs.
 * Handlers for these events are registered in the Minecraft module (MinecraftEventHandler).
 *
 * @author Xujiayao
 */
public final class CoreEvents {

	private CoreEvents() {
	}

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
	 * Posted when a Discord chat message should be displayed in Minecraft.
	 * <p>
	 * The handler should convert the pre-built {@link TextSegment} lists into
	 * Minecraft Components and broadcast them to all online players.
	 *
	 * @param segments                 The main message line segments.
	 * @param replySegments            The reply context line segments (may be null or empty).
	 * @param mentionNotificationText  The mention notification text (may be null).
	 * @param mentionNotificationStyle The notification style: "action_bar", "title", or "chat".
	 * @param mentionedPlayerUuids     UUIDs of players who should receive mention notifications.
	 */
	public record DiscordChatMessageEvent(
			List<TextSegment> segments,
			List<TextSegment> replySegments,
			String mentionNotificationText,
			String mentionNotificationStyle,
			List<String> mentionedPlayerUuids,
			boolean mentionEveryone
	) {
	}

	/**
	 * Posted when a Discord command execution notification should be displayed in Minecraft.
	 * <p>
	 * The handler should convert the pre-built {@link TextSegment} list into
	 * Minecraft Components and broadcast them to all online players.
	 *
	 * @param segments The command notification segments.
	 */
	public record DiscordCommandEvent(
			List<TextSegment> segments
	) {
	}

	/**
	 * Posted when a Discord reaction event should be displayed in Minecraft.
	 *
	 * @param segments      The reaction notification segments.
	 * @param replySegments The reference to the original message.
	 */
	public record DiscordReactionEvent(
			List<TextSegment> segments,
			List<TextSegment> replySegments
	) {
	}

	/**
	 * Posted when a Discord message edit should be displayed in Minecraft.
	 *
	 * @param segments              The edit notification segments.
	 * @param replySegments         The reference to the original (pre-edit) message.
	 * @param editedMessageSegments The new (edited) message formatted as chat.
	 */
	public record DiscordEditEvent(
			List<TextSegment> segments,
			List<TextSegment> replySegments,
			List<TextSegment> editedMessageSegments
	) {
	}

	/**
	 * Posted when a Discord message deletion should be displayed in Minecraft.
	 *
	 * @param segments      The delete notification segments.
	 * @param replySegments The reference to the deleted message.
	 */
	public record DiscordDeleteEvent(
			List<TextSegment> segments,
			List<TextSegment> replySegments
	) {
	}
}
