package com.xujiayao.discord_mc_chat.platform;

import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The platform implementation used when no Minecraft platform is present.
 * <p>
 * This is the case for the standalone DMCC server, which talks to Discord and to DMCC clients
 * but never renders anything inside a game. Every method is intentionally a no-op, which keeps
 * the standalone code path free of null checks while making it obvious that no game-side action
 * can happen without a platform.
 *
 * @author Xujiayao
 */
public final class NoopPlatformHost implements PlatformHost {

	/**
	 * Shared instance.
	 */
	public static final NoopPlatformHost INSTANCE = new NoopPlatformHost();

	private NoopPlatformHost() {
	}

	@Override
	public String name() {
		return "standalone";
	}

	@Override
	public StatsProvider stats() {
		return null;
	}

	@Override
	public void executeCommand(CommandSender sender, String commandLine, CompletableFuture<Void> completion) {
		completion.complete(null);
	}

	@Override
	public void autoCompleteCommand(String input, int opLevel, List<String> suggestions) {
		// No game attached, nothing to complete.
	}

	@Override
	public void sendLinkCode(String playerUuid, String code, boolean alreadyLinked, String discordName) {
		// No game attached, nothing to notify.
	}

	@Override
	public void sendUnlinkResult(String playerUuid, boolean success, String discordName) {
		// No game attached, nothing to notify.
	}

	@Override
	public void applyOpLevels(Map<String, Integer> opLevels) {
		// No game attached, nothing to apply.
	}

	@Override
	public void broadcastDiscordChat(List<TextSegment> segments, List<TextSegment> replySegments,
	                                 String mentionText, String mentionStyle,
	                                 List<String> mentionedUuids, boolean mentionEveryone) {
		// No game attached, nothing to render.
	}

	@Override
	public void broadcastDiscordCommand(List<TextSegment> segments) {
		// No game attached, nothing to render.
	}

	@Override
	public void broadcastDiscordReaction(List<TextSegment> segments, List<TextSegment> replySegments) {
		// No game attached, nothing to render.
	}

	@Override
	public void broadcastDiscordEdit(List<TextSegment> segments, List<TextSegment> replySegments,
	                                 List<TextSegment> editedMessageSegments) {
		// No game attached, nothing to render.
	}

	@Override
	public void broadcastDiscordDelete(List<TextSegment> segments, List<TextSegment> replySegments) {
		// No game attached, nothing to render.
	}

	@Override
	public void broadcastMinecraftRelay(List<TextSegment> segments, String componentJson,
	                                    String componentPlaceholder, String mentionText,
	                                    String mentionStyle, List<String> mentionedUuids,
	                                    boolean mentionEveryone) {
		// No game attached, nothing to render.
	}
}
