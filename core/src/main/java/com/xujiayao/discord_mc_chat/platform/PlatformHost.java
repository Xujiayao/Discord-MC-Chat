package com.xujiayao.discord_mc_chat.platform;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The contract between DMCC's platform-independent core and a concrete platform
 * implementation (Fabric, NeoForge, and in the future server plugins such as Paper).
 * <p>
 * DMCC core never imports a platform API. Whenever it needs to do something that only a
 * platform can do — executing a command inside Minecraft, rendering a message for players,
 * applying OP levels — it goes through this interface. Platforms implement it and register
 * it once via {@link Platform#set(PlatformHost)}.
 * <p>
 * A platform may legitimately implement only part of this interface. Implementations should
 * degrade gracefully (log once and do nothing) instead of throwing when a capability is not
 * available on that platform.
 *
 * @author Xujiayao
 */
public interface PlatformHost {

	/**
	 * A short platform identifier used in logs, e.g. {@code "fabric"} or {@code "neoforge"}.
	 */
	String name();

	/**
	 * Gets the platform's statistics provider, if the platform can supply Minecraft statistics.
	 *
	 * @return The stats provider, or {@code null} when statistics are unavailable.
	 */
	StatsProvider stats();

	// ===== Minecraft command execution bridge =====

	/**
	 * Executes a command on the platform as if it came from the given sender, capturing output.
	 *
	 * @param completion Completed once the command has been executed and its output collected.
	 */
	void executeCommand(CommandSender sender, String commandLine, CompletableFuture<Void> completion);

	/**
	 * Requests platform-side command auto-completion for the given input.
	 *
	 * @param suggestions Mutable list the platform appends its suggestions to.
	 */
	void autoCompleteCommand(String input, int opLevel, List<String> suggestions);

	// ===== Account linking feedback =====

	/**
	 * Notifies a Minecraft player about their account linking verification code.
	 *
	 * @param discordName The linked Discord user's name, or an empty string.
	 */
	void sendLinkCode(String playerUuid, String code, boolean alreadyLinked, String discordName);

	/**
	 * Notifies a Minecraft player about an unlink result.
	 */
	void sendUnlinkResult(String playerUuid, boolean success, String discordName);

	/**
	 * Applies DMCC's authoritative OP levels to the platform's player permission list.
	 */
	void applyOpLevels(Map<String, Integer> opLevels);

	// ===== Discord message rendering =====

	/**
	 * Broadcasts a Discord chat message (with optional reply line and mention notification).
	 *
	 * @param replySegments   The reply line segments, or {@code null}.
	 * @param mentionText     The mention notification text, or {@code null} when nobody was mentioned.
	 */
	void broadcastDiscordChat(List<TextSegment> segments,
							  List<TextSegment> replySegments,
							  String mentionText,
							  String mentionStyle,
							  List<String> mentionedUuids,
							  boolean mentionEveryone);

	/**
	 * Broadcasts a Discord slash command notification.
	 */
	void broadcastDiscordCommand(List<TextSegment> segments);

	/**
	 * Broadcasts a Discord reaction notification.
	 *
	 * @param replySegments The reply line segments, or {@code null}.
	 */
	void broadcastDiscordReaction(List<TextSegment> segments, List<TextSegment> replySegments);

	/**
	 * Broadcasts a Discord message edit notification.
	 *
	 * @param replySegments         The reply line segments, or {@code null}.
	 * @param editedMessageSegments The new message content segments, or {@code null}.
	 */
	void broadcastDiscordEdit(List<TextSegment> segments,
							  List<TextSegment> replySegments,
							  List<TextSegment> editedMessageSegments);

	/**
	 * Broadcasts a Discord message deletion notification.
	 *
	 * @param replySegments The reply line segments, or {@code null}.
	 */
	void broadcastDiscordDelete(List<TextSegment> segments, List<TextSegment> replySegments);

	/**
	 * Broadcasts a message relayed from another DMCC client.
	 *
	 * @param componentJson        Serialized component JSON, or {@code null}.
	 * @param componentPlaceholder Placeholder inside {@code componentJson}, or {@code null}.
	 * @param mentionText          The mention notification text, or {@code null}.
	 */
	void broadcastMinecraftRelay(List<TextSegment> segments,
								 String componentJson,
								 String componentPlaceholder,
								 String mentionText,
								 String mentionStyle,
								 List<String> mentionedUuids,
								 boolean mentionEveryone);
}
