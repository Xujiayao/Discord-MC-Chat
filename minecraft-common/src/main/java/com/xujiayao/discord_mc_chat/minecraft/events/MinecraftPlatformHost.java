package com.xujiayao.discord_mc_chat.minecraft.events;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.platform.PlatformHost;
import com.xujiayao.discord_mc_chat.platform.StatsProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The single adapter between DMCC core and the Minecraft-side implementation.
 * <p>
 * DMCC core only knows the platform-independent {@link PlatformHost} contract, so every game-side
 * action requested by core is translated here into a call on {@link MinecraftEventHandler}. This
 * adapter lives in the shared {@code minecraft-common} source directory and is therefore compiled
 * by both the Fabric module and the NeoForge module, each of which registers its own instance
 * under its own platform name.
 * <p>
 * The opposite direction, that is mixins and server lifecycle callbacks, talks to
 * {@link MinecraftEventHandler} directly; this class only covers the core-facing direction.
 *
 * @author Xujiayao
 */
public final class MinecraftPlatformHost implements PlatformHost {

	private final String platformName;

	/**
	 * Creates the platform host of one loader module.
	 *
	 * @param platformName The platform identifier used by core in logs, e.g. {@code "fabric"} or {@code "neoforge"}.
	 */
	public MinecraftPlatformHost(String platformName) {
		this.platformName = platformName;
	}

	@Override
	public String name() {
		return platformName;
	}

	@Override
	public StatsProvider stats() {
		return MinecraftEventHandler.statsProvider();
	}

	@Override
	public void executeCommand(CommandSender sender, String commandLine, CompletableFuture<Void> completion) {
		MinecraftEventHandler.executeCommand(sender, commandLine, completion);
	}

	@Override
	public void autoCompleteCommand(String input, int opLevel, List<String> suggestions) {
		MinecraftEventHandler.autoCompleteCommand(input, opLevel, suggestions);
	}

	@Override
	public void sendLinkCode(String playerUuid, String code, boolean alreadyLinked, String discordName) {
		MinecraftEventHandler.sendLinkCode(playerUuid, code, alreadyLinked, discordName);
	}

	@Override
	public void sendUnlinkResult(String playerUuid, boolean success, String discordName) {
		MinecraftEventHandler.sendUnlinkResult(playerUuid, success, discordName);
	}

	@Override
	public void applyOpLevels(Map<String, Integer> opLevels) {
		MinecraftEventHandler.applyOpLevels(opLevels);
	}

	@Override
	public void broadcastDiscordChat(List<TextSegment> segments,
	                                 List<TextSegment> replySegments,
	                                 String mentionText,
	                                 String mentionStyle,
	                                 List<String> mentionedUuids,
	                                 boolean mentionEveryone) {
		MinecraftEventHandler.broadcastDiscordChat(segments, replySegments, mentionText, mentionStyle,
				mentionedUuids, mentionEveryone);
	}

	@Override
	public void broadcastDiscordCommand(List<TextSegment> segments) {
		MinecraftEventHandler.broadcastDiscordCommand(segments);
	}

	@Override
	public void broadcastDiscordReaction(List<TextSegment> segments, List<TextSegment> replySegments) {
		MinecraftEventHandler.broadcastDiscordReaction(segments, replySegments);
	}

	@Override
	public void broadcastDiscordEdit(List<TextSegment> segments,
	                                 List<TextSegment> replySegments,
	                                 List<TextSegment> editedMessageSegments) {
		MinecraftEventHandler.broadcastDiscordEdit(segments, replySegments, editedMessageSegments);
	}

	@Override
	public void broadcastDiscordDelete(List<TextSegment> segments, List<TextSegment> replySegments) {
		MinecraftEventHandler.broadcastDiscordDelete(segments, replySegments);
	}

	@Override
	public void broadcastMinecraftRelay(List<TextSegment> segments,
	                                    String componentJson,
	                                    String componentPlaceholder,
	                                    String mentionText,
	                                    String mentionStyle,
	                                    List<String> mentionedUuids,
	                                    boolean mentionEveryone) {
		MinecraftEventHandler.broadcastMinecraftRelay(segments, componentJson, componentPlaceholder, mentionText,
				mentionStyle, mentionedUuids, mentionEveryone);
	}
}
