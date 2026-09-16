package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.CommandTargets;
import com.xujiayao.discord_mc_chat.commands.impl.ConsoleCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.protocol.CommandFileAssembler;
import com.xujiayao.discord_mc_chat.network.protocol.Packet;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.server.discord.BotPresenceManager;
import com.xujiayao.discord_mc_chat.server.discord.ChannelUpdateManager;
import com.xujiayao.discord_mc_chat.server.discord.DiscordConsoleForwarder;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.DiscordMessageAdapter;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.OpSyncManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.server.message.MessageParserCommon;
import com.xujiayao.discord_mc_chat.server.message.MinecraftMessageParser;
import com.xujiayao.discord_mc_chat.update.UpdateCheckManager;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
final class ServerHandler extends SimpleChannelInboundHandler<Packet> {
	private static final String TELLRAW_COMPONENT_PLACEHOLDER = "__DMCC_TELLRAW_COMPONENT__";

	// Compiled broadcasts.excluded_commands patterns, rebuilt only when the configured list changes.
	private static volatile List<Pattern> excludedCommandPatterns = List.of();
	private static volatile String excludedCommandFingerprint = null;

	private final NettyServer server;
	private String expectedNonce;
	private boolean authenticated = false;
	private boolean initialOpSyncDone = false;
	private String clientName;

	ServerHandler(NettyServer server) {
		this.server = server;
	}

	private static String[] toStringArray(Object[] args) {
		String[] out = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			out[i] = String.valueOf(args[i]);
		}
		return out;
	}

	/**
	 * @return Whether the command matches one of the configured {@code broadcasts.excluded_commands} patterns.
	 */
	private static boolean isExcludedMinecraftCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}
		for (Pattern pattern : excludedCommandPatterns()) {
			if (pattern.matcher(command).matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return The compiled {@code broadcasts.excluded_commands} patterns, recompiled only when the configured
	 * list actually changes. The patterns used to be recompiled for every single chat command.
	 */
	private static List<Pattern> excludedCommandPatterns() {
		List<String> sources = new ArrayList<>();
		JsonNode excludedCommands = ConfigManager.getConfigNode("broadcasts.excluded_commands");
		if (excludedCommands.isArray()) {
			for (JsonNode node : excludedCommands) {
				if (node != null && node.isString() && !node.asString("").isBlank()) {
					sources.add(node.asString(""));
				}
			}
		}

		String fingerprint = String.join("\u0000", sources);
		if (fingerprint.equals(excludedCommandFingerprint)) {
			return excludedCommandPatterns;
		}

		List<Pattern> compiled = new ArrayList<>();
		for (String source : sources) {
			try {
				compiled.add(Pattern.compile(source));
			} catch (Exception e) {
				LOGGER.warn(I18nManager.getDmccTranslation("server.network.invalid_excluded_command_regex", source));
			}
		}
		excludedCommandPatterns = List.copyOf(compiled);
		excludedCommandFingerprint = fingerprint;
		return excludedCommandPatterns;
	}

	/**
	 * Resolves the relay destinations of one Minecraft event.
	 * <p>
	 * Cross-client relay only exists in standalone mode, and the source echo additionally depends on
	 * {@code overwrite_minecraft_source_messages} unless the event forces it.
	 *
	 * @param broadcastNode            Config node under {@code broadcasts.minecraft_to_minecraft}.
	 * @param canOverwriteEchoToSource Whether the overwrite switch is allowed to trigger the source echo.
	 * @param forceEchoToSource        Whether the source echo happens regardless of the overwrite switch.
	 */
	private static RelayTargets relayTargets(String broadcastNode, boolean canOverwriteEchoToSource,
											 boolean forceEchoToSource) {
		boolean overwrite = ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");
		boolean echoToSource = (overwrite && canOverwriteEchoToSource) || forceEchoToSource;
		boolean toOtherClients = "standalone".equals(ConfigManager.getMode())
				&& ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft." + broadcastNode);
		return new RelayTargets(toOtherClients, echoToSource);
	}

	/**
	 * Builds a relay packet carrying the optional mention notification payload.
	 */
	private static Packets.MinecraftRelay relay(List<TextSegment> segments, String mentionText, String mentionStyle,
												List<String> mentionedUuids, boolean mentionEveryone) {
		return new Packets.MinecraftRelay(segments, null, null, null, mentionText, mentionStyle, mentionedUuids, mentionEveryone);
	}

	/**
	 * Builds a relay packet carrying the serialized tellraw component when one is available.
	 */
	private static Packets.MinecraftRelay tellRawRelay(List<TextSegment> segments, String componentJson,
													   String componentText, boolean useSerializedComponent) {
		if (!useSerializedComponent) {
			return new Packets.MinecraftRelay(segments);
		}
		return new Packets.MinecraftRelay(segments, componentJson, TELLRAW_COMPONENT_PLACEHOLDER, componentText,
				null, null, null, false);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		// Wait for handshake
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		boolean announceConsoleForwardingStop = authenticated
				&& clientName != null
				&& isConsoleForwardingEnabledForClient(clientName);

		if (clientName != null) {
			LOGGER.warn(I18nManager.getDmccTranslation("server.network.client_disconnected_normal", clientName));
		}
		if (announceConsoleForwardingStop) {
			DiscordConsoleForwarder.sendStatus(clientName, false);
		}
		// Clean up from NetworkManager
		NetworkManager.removeClientChannel(ctx.channel());
		BotPresenceManager.update();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof Packets.KeepAlive) {
			// No-op, just resets idle timer
			return;
		}

		String unexpectedPacketMessage = I18nManager.getDmccTranslation("server.network.unexpected_packet", clientName, packet == null ? "null" : packet.type().name());

		if (authenticated) {
			switch (packet) {
				case Packets.ConsoleLogBatch p -> DiscordConsoleForwarder.sendBatch(clientName, p.lines());
				case Packets.MinecraftEvent p -> handleMinecraftEvent(ctx, p);
				case Packets.InfoSnapshot p -> NetworkManager.cacheInfoResponse(clientName, p);
				case Packets.LatencyPing p -> ctx.writeAndFlush(new Packets.LatencyPong(p.sentAtMillis()));
				case Packets.CommandResult p -> handleCommandResult(p, unexpectedPacketMessage);
				case Packets.CommandRequest p -> handleCommandRequest(ctx, p, unexpectedPacketMessage);
				case Packets.CommandFileChunk p -> CommandFileAssembler.accept(p);
				case Packets.AutoCompleteResult p -> handleAutoCompleteResult(p);
				case Packets.LinkRequest p -> handleLinkRequest(ctx, p);
				case Packets.UnlinkRequest p -> handleUnlinkRequest(ctx, p);
				case null, default -> LOGGER.warn(unexpectedPacketMessage);
			}
		} else {
			switch (packet) {
				case Packets.Handshake p -> handleHandshake(ctx, p);
				case Packets.AuthResponse p -> handleAuthResponse(ctx, p);
				case null, default -> LOGGER.warn(unexpectedPacketMessage);
			}
		}
	}

	// --- Authentication --------------------------------------------------------------------------

	private void handleHandshake(ChannelHandlerContext ctx, Packets.Handshake packet) {
		if ("single_server".equals(ConfigManager.getMode())) {
			if (!"Internal".equals(packet.serverName())) {
				reject(ctx, packet.serverName(), "server.network.disconnect_reasons.single_server_mode", packet.serverName());
				return;
			}
		} else {
			if (!isWhitelisted(packet.serverName())) {
				reject(ctx, packet.serverName(), "server.network.disconnect_reasons.not_whitelisted", packet.serverName());
				return;
			}
			if (NetworkManager.isClientConnected(packet.serverName())) {
				reject(ctx, packet.serverName(), "server.network.disconnect_reasons.duplicate_name", packet.serverName());
				return;
			}
			if (!Constants.VERSION.equals(packet.dmccVersion())) {
				reject(ctx, packet.serverName(), "server.network.disconnect_reasons.version_mismatch",
						"DMCC", packet.dmccVersion(), Constants.VERSION);
				return;
			}
			if (!getMinecraftVersion(packet.serverName()).equals(packet.minecraftVersion())) {
				reject(ctx, packet.serverName(), "server.network.disconnect_reasons.version_mismatch",
						"Minecraft", packet.minecraftVersion(), getMinecraftVersion(packet.serverName()));
				return;
			}
		}

		this.clientName = packet.serverName();
		this.expectedNonce = CryptUtils.generateRandomString(16);
		ctx.writeAndFlush(new Packets.Challenge(this.expectedNonce));
	}

	private void handleAuthResponse(ChannelHandlerContext ctx, Packets.AuthResponse packet) {
		if (!CryptUtils.sha256(this.expectedNonce + server.getSharedSecret()).equals(packet.hash())) {
			reject(ctx, clientName, "server.network.disconnect_reasons.auth_failed");
			return;
		}

		this.authenticated = true;
		NetworkManager.addClientChannel(ctx.channel(), clientName);

		LOGGER.info(I18nManager.getDmccTranslation("server.network.auth_success", clientName));
		boolean consoleForwardingEnabled = isConsoleForwardingEnabledForClient(clientName);
		ctx.writeAndFlush(new Packets.LoginSuccess(
				ConfigManager.getString("language"),
				ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages"),
				consoleForwardingEnabled
		));
		if (consoleForwardingEnabled) {
			DiscordConsoleForwarder.sendStatus(clientName, true);
		}
		BotPresenceManager.update();
	}

	/**
	 * Logs a rejected connection, tells the peer why and closes the channel.
	 */
	private void reject(ChannelHandlerContext ctx, String peerName, String reasonKey, Object... args) {
		LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", peerName,
				I18nManager.getDmccTranslation(reasonKey, args)));
		ctx.writeAndFlush(new Packets.Disconnect(reasonKey, toStringArray(args)));
		ctx.close();
	}


	// --- Authenticated traffic -------------------------------------------------------------------

	private void handleMinecraftEvent(ChannelHandlerContext ctx, Packets.MinecraftEvent packet) {
		switch (packet.eventType()) {
			// Server events
			case SERVER_STARTED -> {
				handleMinecraftSystemMessage(packet, clientName, "server.started", "server.start", false, false);
				BotPresenceManager.update();
			}
			case SERVER_STOPPING -> {
				handleMinecraftSystemMessage(packet, clientName, "server.stopped", "server.stop", false, false);
				if ("single_server".equals(ConfigManager.getMode())) {
					ChannelUpdateManager.updateOfflineForSingleServerShutdownAndWait();
				}
				ctx.close();
			}
			// Player events
			case PLAYER_JOIN -> {
				handleMinecraftSystemMessage(packet, clientName, "player.join", "player.join", false, false);

				// Perform the initial OP sync once, on the first player join for this DMCC client.
				if (!initialOpSyncDone) {
					initialOpSyncDone = true;
					OpSyncManager.syncAll();
				}

				BotPresenceManager.update();
			}
			case PLAYER_QUIT -> {
				handleMinecraftSystemMessage(packet, clientName, "player.quit", "player.quit", false, false);
				BotPresenceManager.update();
			}
			case PLAYER_DIE ->
					handleMinecraftSystemMessage(packet, clientName, "player.die", "player.die", false, false);
			case PLAYER_ADVANCEMENT -> handleMinecraftSystemMessage(packet, clientName, "player.advancement",
					"player.advancement." + packet.placeholders().getOrDefault("type", ""), false, false);
			case PLAYER_CHANGE_GAME_MODE -> handleMinecraftSystemMessage(packet, clientName, "player.change_game_mode",
					"player.change_game_mode", false, ConfigManager.getBoolean("broadcasts.echo_player_change_game_mode_to_source"));
			case PLAYER_CHAT -> handleMinecraftUserMessage(packet, clientName, "player.chat");
			case PLAYER_COMMAND -> handleMinecraftCommandMessage(packet, clientName);
			case SOURCE_SAY -> handleMinecraftUserMessage(packet, clientName, "source.say");
			case SOURCE_TELL_RAW -> handleMinecraftTellRawMessage(packet, clientName);
			case SOURCE_MSG -> handleMinecraftUserMessage(packet, clientName, "source.msg");
			case SOURCE_ME -> handleMinecraftSystemMessage(packet, clientName, "source.me", "source.me", true, false);
		}
	}

	/**
	 * Routes a result coming back from a client to the request that is waiting for it.
	 */
	private void handleCommandResult(Packets.CommandResult result, String unexpectedPacketMessage) {
		switch (result.kind()) {
			case EXECUTE -> {
				byte[] fileData = result.hasFile() ? CommandFileAssembler.take(result.requestId()) : null;
				ExecuteCommand.completeRequest(result.requestId(), result, fileData);
			}
			case CONSOLE -> ConsoleCommand.completeRequest(result.requestId(), result);
			// UPDATE_CHECK results only travel from the server to a client.
			case UPDATE_CHECK -> LOGGER.warn(unexpectedPacketMessage);
		}
	}

	private void handleCommandRequest(ChannelHandlerContext ctx, Packets.CommandRequest request, String unexpectedPacketMessage) {
		if (request.kind() != Packets.RpcKind.UPDATE_CHECK) {
			// CONSOLE/EXECUTE requests only travel from the server to a client.
			LOGGER.warn(unexpectedPacketMessage);
			return;
		}
		UpdateCheckManager.CheckResult result = UpdateCheckManager.checkNow();
		ctx.writeAndFlush(Packets.CommandResult.text(Packets.RpcKind.UPDATE_CHECK, request.requestId(), result.message()));
	}

	private void handleAutoCompleteResult(Packets.AutoCompleteResult result) {
		switch (result.kind()) {
			case EXECUTE -> NetworkManager.cacheExecuteAutoCompleteResponse(clientName, result.suggestions());
			case CONSOLE -> NetworkManager.cacheConsoleAutoCompleteResponse(clientName, result.suggestions());
			case UPDATE_CHECK -> {
			}
		}
	}

	private void handleLinkRequest(ChannelHandlerContext ctx, Packets.LinkRequest request) {
		if (!LinkedAccountManager.isMinecraftUuidLinked(request.minecraftUuid())) {
			String code = VerificationCodeManager.generateOrRefreshCode(request.minecraftUuid(), request.playerName());
			ctx.writeAndFlush(new Packets.LinkResult(request.minecraftUuid(), code, false, ""));
			return;
		}
		if (request.joinCheck()) {
			// Join checks stay silent: only an explicit /dmcc link command reports "already linked".
			return;
		}
		String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(request.minecraftUuid());
		String discordName = DiscordManager.resolveDiscordUserName(discordId != null ? discordId : "");
		ctx.writeAndFlush(new Packets.LinkResult(request.minecraftUuid(), null, true, discordName));
	}

	private void handleUnlinkRequest(ChannelHandlerContext ctx, Packets.UnlinkRequest request) {
		String unlinkedDiscordId = LinkedAccountManager.unlinkByMinecraftUuid(request.minecraftUuid(), request.playerName());
		String discordName = unlinkedDiscordId != null ? DiscordManager.resolveDiscordUserName(unlinkedDiscordId) : "";
		ctx.writeAndFlush(new Packets.UnlinkResult(request.minecraftUuid(), unlinkedDiscordId != null, discordName));
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.READER_IDLE) {
				LOGGER.error(I18nManager.getDmccTranslation("server.network.client_timeout", clientName != null ? clientName : "unknown"));
				ctx.close();
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error(I18nManager.getDmccTranslation("server.network.exception_caught"), cause);
		ctx.close();
	}

	private boolean isWhitelisted(String serverName) {
		return CommandTargets.isConfiguredServer(serverName);
	}

	private String getMinecraftVersion(String serverName) {
		JsonNode config = CommandTargets.findServerConfig(serverName);
		return config != null ? config.path("minecraft_version").asString() : "";
	}

	private void handleMinecraftUserMessage(Packets.MinecraftEvent packet,
											String sourceClientName,
											String channelNode) {
		String rawContent = packet.placeholders().getOrDefault("message", "");
		String displayName = packet.placeholders().getOrDefault("display_name", packet.placeholders().getOrDefault("player_name", "Unknown"));
		String roleColor = resolveDisplayRoleColor(packet.placeholders().getOrDefault("player_uuid", ""));

		boolean parseForMinecraft = true;
		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseMessage(rawContent, parseForMinecraft);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());

		Map<String, String> discordPlaceholders = new HashMap<>(packet.placeholders());
		discordPlaceholders.put("server", sourceClientName);
		discordPlaceholders.put("message", parsed.discordContent());
		DiscordManager.sendMinecraftUserMessage(sourceClientName, channelNode, discordPlaceholders);

		broadcastMinecraftRelay(packet, sourceClientName, relaySegments, overwriteSegments, parsed, true, false, true, channelNode);
	}

	private void handleMinecraftCommandMessage(Packets.MinecraftEvent packet, String sourceClientName) {
		String command = packet.placeholders().getOrDefault("command", "");
		if (isExcludedMinecraftCommand(command)) {
			return;
		}

		String displayName = packet.placeholders().getOrDefault("display_name", packet.placeholders().getOrDefault("player_name", "Unknown"));
		String roleColor = resolveDisplayRoleColor(packet.placeholders().getOrDefault("player_uuid", ""));
		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseCommandMessage(command);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());

		Map<String, String> discordPlaceholders = new HashMap<>(packet.placeholders());
		discordPlaceholders.put("server", sourceClientName);
		discordPlaceholders.put("message", parsed.discordContent());
		DiscordManager.sendMinecraftUserMessage(sourceClientName, "player.command", discordPlaceholders);

		boolean echoPlayerCommandToSource = ConfigManager.getBoolean("broadcasts.echo_player_command_to_source");
		broadcastMinecraftRelay(packet, sourceClientName, relaySegments, overwriteSegments, parsed, false, echoPlayerCommandToSource, false, "player.command");
	}

	private void handleMinecraftTellRawMessage(Packets.MinecraftEvent packet, String sourceClientName) {
		String translatedMessage = packet.placeholders().getOrDefault("message", "");
		String componentJson = packet.placeholders().getOrDefault("component_json", "");
		boolean useSerializedComponent = !componentJson.isBlank();

		List<TextSegment> relaySegments = MinecraftMessageParser.buildSystemMessageSegments(
				sourceClientName,
				List.of(new TextSegment(useSerializedComponent ? TELLRAW_COMPONENT_PLACEHOLDER : translatedMessage))
		);
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteSystemMessageSegments(
				sourceClientName,
				List.of(new TextSegment(useSerializedComponent ? TELLRAW_COMPONENT_PLACEHOLDER : translatedMessage))
		);

		DiscordManager.sendMinecraftSystemMessage(sourceClientName, "source.tell_raw", translatedMessage);

		broadcastMinecraftTellRawRelay(sourceClientName, relaySegments, overwriteSegments, componentJson, translatedMessage, useSerializedComponent);
	}

	private void handleMinecraftSystemMessage(Packets.MinecraftEvent packet, String sourceClientName, String channelNode,
											  String lang, boolean canOverwriteEchoToSource, boolean forceEchoToSource) {
		String message;
		if (packet.eventType() == Packets.MinecraftEventType.SOURCE_ME) {
			String rawAction = packet.placeholders().getOrDefault("action", "");
			String displayName = packet.placeholders().getOrDefault("display_name", packet.placeholders().getOrDefault("player_name", "Unknown"));
			message = (displayName + " " + rawAction).trim();
		} else {
			message = resolveMinecraftToDiscordMessage(lang, packet.placeholders());
		}

		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseMessage(message, true);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildSystemMessageSegments(sourceClientName, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteSystemMessageSegments(sourceClientName, parsed.minecraftSegments());

		Map<String, String> placeholders = new HashMap<>(packet.placeholders());
		if (packet.eventType() == Packets.MinecraftEventType.SOURCE_ME) {
			placeholders.put("action", parsed.discordContent());
		}
		DiscordManager.clientBroadcast(sourceClientName, channelNode, lang, placeholders);

		broadcastMinecraftRelay(packet, sourceClientName, relaySegments, overwriteSegments, parsed, canOverwriteEchoToSource, forceEchoToSource, true, channelNode);
	}

	private String resolveMinecraftToDiscordMessage(String lang, Map<String, String> placeholders) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}

		String[] parts = ("minecraft_to_xxxxx." + lang).split("\\.");
		JsonNode messageNode = customMessages;
		for (String part : parts) {
			messageNode = messageNode.path(part);
		}

		String message = messageNode.asString("");
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return message;
	}

	private void broadcastMinecraftRelay(Packets.MinecraftEvent packet,
										 String sourceClientName,
										 List<TextSegment> relaySegments,
										 List<TextSegment> overwriteSegments,
										 MinecraftMessageParser.ParsedMessage parsed,
										 boolean canOverwriteEchoToSource,
										 boolean forceEchoToSource,
										 boolean parseMentionsForNotifications,
										 String broadcastNode) {
		RelayTargets targets = relayTargets(broadcastNode, canOverwriteEchoToSource, forceEchoToSource);
		if (targets.none()) {
			return;
		}

		boolean notifyMentions = parseMentionsForNotifications
				&& (parsed.mentionEveryone() || !parsed.mentionedPlayerUuids().isEmpty())
				&& ConfigManager.getBoolean("account_linking.mention_notifications.enable");

		String displayName = notifyMentions ? packet.placeholders().getOrDefault("display_name", "Unknown") : null;
		String mentionText = notifyMentions ? MessageParserCommon.mentionNotification(displayName) : null;
		String mentionStyle = notifyMentions
				? ConfigManager.getString("account_linking.mention_notifications.style", "title")
				: null;
		List<String> mentionedUuids = notifyMentions ? List.copyOf(parsed.mentionedPlayerUuids()) : null;
		boolean mentionEveryone = notifyMentions && parsed.mentionEveryone();

		if (targets.toOtherClients()) {
			NetworkManager.broadcastToClientsExcept(
					relay(relaySegments, mentionText, mentionStyle, mentionedUuids, mentionEveryone), sourceClientName);
		}

		if (targets.echoToSource()) {
			NetworkManager.sendPacketToClient(
					relay(overwriteSegments, mentionText, mentionStyle, mentionedUuids, mentionEveryone), sourceClientName);
		}
	}

	private void broadcastMinecraftTellRawRelay(String sourceClientName,
												List<TextSegment> relaySegments,
												List<TextSegment> overwriteSegments,
												String componentJson,
												String componentText,
												boolean useSerializedComponent) {
		// tellraw echoes to the source whenever the overwrite switch is on, without the extra per-event gate.
		RelayTargets targets = relayTargets("source.tell_raw", true, false);
		if (targets.none()) {
			return;
		}

		if (targets.toOtherClients()) {
			NetworkManager.broadcastToClientsExcept(tellRawRelay(relaySegments, componentJson, componentText, useSerializedComponent), sourceClientName);
		}

		if (targets.echoToSource()) {
			NetworkManager.sendPacketToClient(tellRawRelay(overwriteSegments, componentJson, componentText, useSerializedComponent), sourceClientName);
		}
	}

	private String resolveDisplayRoleColor(String playerUuid) {
		if (!ConfigManager.getBoolean("account_linking.use_discord_role_color_for_mc_chats")) {
			return "white";
		}
		if (playerUuid == null || playerUuid.isBlank()) {
			return "white";
		}
		String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(playerUuid);
		if (discordId == null || discordId.isBlank()) {
			return "white";
		}
		return DiscordMessageAdapter.roleColorHex(DiscordManager.retrieveMember(discordId));
	}

	private boolean isConsoleForwardingEnabledForClient(String serverName) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return false;
		}

		if ("standalone".equals(ConfigManager.getMode())) {
			JsonNode channelsNode = ConfigManager.getConfigNode("console_forwarding.channels");
			if (!channelsNode.isArray()) {
				return false;
			}
			for (JsonNode node : channelsNode) {
				if (serverName.equals(node.path("server").asString("")) && !node.path("channel").asString("").isBlank()) {
					return true;
				}
			}
			return false;
		}

		return !ConfigManager.getString("console_forwarding.channel", "").isBlank();
	}

	/**
	 * Where a relayed Minecraft message has to go.
	 *
	 * @param toOtherClients Whether the other connected clients should receive it.
	 * @param echoToSource   Whether the originating client should receive the DMCC-rendered echo.
	 */
	private record RelayTargets(boolean toOtherClients, boolean echoToSource) {

		private boolean none() {
			return !toOtherClients && !echoToSource;
		}
	}
}
