package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.impl.ConsoleCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.AuthPackets.AuthResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.AuthPackets.ChallengePacket;
import com.xujiayao.discord_mc_chat.network.packets.AuthPackets.DisconnectPacket;
import com.xujiayao.discord_mc_chat.network.packets.AuthPackets.HandshakePacket;
import com.xujiayao.discord_mc_chat.network.packets.AuthPackets.LoginSuccessPacket;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.ConsoleLogBatchPacket;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftRelayPacket;
import com.xujiayao.discord_mc_chat.network.packets.MiscPackets.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.MiscPackets.LatencyPingPacket;
import com.xujiayao.discord_mc_chat.network.packets.MiscPackets.LatencyPongPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.server.discord.BotPresenceManager;
import com.xujiayao.discord_mc_chat.server.discord.ChannelUpdateManager;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.OpSyncManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.server.message.MinecraftMessageParser;
import com.xujiayao.discord_mc_chat.update.UpdateCheckManager;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import tools.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
final class ServerHandler extends SimpleChannelInboundHandler<Packet> {
	private static final String TELLRAW_COMPONENT_PLACEHOLDER = "__DMCC_TELLRAW_COMPONENT__";

	private static final int EXCLUDED_COMMAND_PATTERN_CACHE_SIZE = 64;

	/**
	 * Upper bound on failed authentication attempts on a single connection. A failure already closes the
	 * channel, but packets decoded in the same read batch are still delivered to this handler, so this stops
	 * them from being answered (and hashed) one by one.
	 */
	private static final int MAX_CONSECUTIVE_AUTH_FAILURES = 3;

	/**
	 * Update checks block on an HTTP request (30 s call timeout), so they must never run on a Netty event
	 * loop. A single thread keeps concurrent checks serialized exactly as they were while running on the
	 * event loop; daemon threads keep an in-flight check from holding up JVM shutdown.
	 */
	private static final ExecutorService UPDATE_CHECK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = ExecutorServiceUtils.newThreadFactory("DMCC-UpdateRequest").newThread(r);
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * Compiled {@code broadcasts.excluded_commands} patterns. Capacity 64, access-order LRU eviction.
	 * Entries never go stale: the key is the configured regex source string itself, so a configuration
	 * reload can only add new entries, never invalidate an existing one.
	 */
	private static final Map<String, Pattern> EXCLUDED_COMMAND_PATTERNS =
			Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
					return size() > EXCLUDED_COMMAND_PATTERN_CACHE_SIZE;
				}
			});

	private final NettyServer server;
	private String expectedNonce;
	private boolean authenticated = false;
	private boolean initialOpSyncDone = false;
	private int authFailureCount = 0;
	private String clientName;

	ServerHandler(NettyServer server) {
		this.server = server;
	}

	private static void applyMentionNotification(MinecraftRelayPacket packet, String displayName, MinecraftMessageParser.ParsedMessage parsed) {
		packet.mentionNotificationText = MinecraftMessageParser.getMentionNotificationText(displayName);
		packet.mentionNotificationStyle = ConfigManager.getString("account_linking.mention_notifications.style", "title");
		packet.mentionedPlayerUuids = List.copyOf(parsed.mentionedPlayerUuids());
		packet.mentionEveryone = parsed.mentionEveryone();
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
			DiscordManager.sendConsoleForwardingStatusMessage(clientName, false);
		}
		NetworkManager.removeClientChannel(ctx.channel());
		BotPresenceManager.update();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof KeepAlivePacket) {
			// No-op, just resets idle timer
			return;
		}

		if (authenticated) {
			switch (packet) {
				case ConsoleLogBatchPacket p -> DiscordManager.sendConsoleForwardedBatchMessage(clientName, p.lines);
				case MinecraftEventPacket p -> {
					switch (p.type) {
						// Server events
						case SERVER_STARTED -> {
							handleMinecraftSystemMessage(p, clientName, "server.started", "server.start", false, false);
							BotPresenceManager.update();
						}
						case SERVER_STOPPING -> {
							handleMinecraftSystemMessage(p, clientName, "server.stopped", "server.stop", false, false);
							if ("single_server".equals(ModeManager.getMode())) {
								ChannelUpdateManager.updateOfflineForSingleServerShutdownAndWait();
							}
							ctx.close();
						}
						// Player events
						case PLAYER_JOIN -> {
							handleMinecraftSystemMessage(p, clientName, "player.join", "player.join", false, false);

							// Perform the initial OP sync once, on the first player join for this DMCC client.
							if (!initialOpSyncDone) {
								initialOpSyncDone = true;
								OpSyncManager.syncAll();
							}

							BotPresenceManager.update();
						}
						case PLAYER_QUIT -> {
							handleMinecraftSystemMessage(p, clientName, "player.quit", "player.quit", false, false);
							BotPresenceManager.update();
						}
						case PLAYER_DIE ->
								handleMinecraftSystemMessage(p, clientName, "player.die", "player.die", false, false);
						case PLAYER_ADVANCEMENT ->
								handleMinecraftSystemMessage(p, clientName, "player.advancement", "player.advancement." + p.placeholders.getOrDefault("type", ""), false, false);
						case PLAYER_CHANGE_GAME_MODE -> {
							boolean forceEchoToSource = ConfigManager.getBoolean("broadcasts.echo_player_change_game_mode_to_source");
							handleMinecraftSystemMessage(p, clientName, "player.change_game_mode", "player.change_game_mode", false, forceEchoToSource);
						}
						case PLAYER_CHAT -> handleMinecraftUserMessage(p, clientName, "player.chat");
						case PLAYER_COMMAND -> handleMinecraftCommandMessage(p, clientName);
						case SOURCE_SAY -> handleMinecraftUserMessage(p, clientName, "source.say");
						case SOURCE_TELL_RAW -> handleMinecraftTellRawMessage(p, clientName);
						case SOURCE_MSG -> handleMinecraftUserMessage(p, clientName, "source.msg");
						case SOURCE_ME ->
								handleMinecraftSystemMessage(p, clientName, "source.me", "source.me", true, false);
					}
				}
				case CommandPackets.Info.ResponsePacket p -> NetworkManager.cacheInfoResponse(clientName, p);
				case LatencyPingPacket p -> ctx.writeAndFlush(new LatencyPongPacket(p.sentAtMillis));
				case CommandPackets.Execute.ResponsePacket p -> ExecuteCommand.completeRequest(p.requestId, p);
				case CommandPackets.Console.ResponsePacket p -> ConsoleCommand.completeRequest(p.requestId, p);
				case CommandPackets.Update.RequestPacket p -> {
					// The check blocks on an HTTP request for up to 30 s, so it must not run on the event loop;
					// writeAndFlush is safe from another thread and still targets the requesting client.
					Channel channel = ctx.channel();
					UPDATE_CHECK_EXECUTOR.execute(() -> channel.writeAndFlush(
							new CommandPackets.Update.ResponsePacket(p.requestId, UpdateCheckManager.checkNow().message())
					));
				}
				case CommandPackets.Execute.AutoCompleteResponsePacket p ->
						NetworkManager.cacheExecuteAutoCompleteResponse(clientName, p.suggestions);
				case CommandPackets.Console.AutoCompleteResponsePacket p ->
						NetworkManager.cacheConsoleAutoCompleteResponse(clientName, p.suggestions);
				case CommandPackets.Link.RequestPacket p -> {
					if (LinkedAccountManager.isMinecraftUuidLinked(p.minecraftUuid)) {
						if (!p.joinCheck) {
							// Only notify "already linked" for explicit /dmcc link commands, not join checks
							String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(p.minecraftUuid);
							String discordName = DiscordManager.resolveDiscordUserName(discordId != null ? discordId : "");
							ctx.writeAndFlush(new CommandPackets.Link.ResponsePacket(p.minecraftUuid, null, true, discordName));
						}
					} else {
						String code = VerificationCodeManager.generateOrRefreshCode(p.minecraftUuid, p.playerName);
						ctx.writeAndFlush(new CommandPackets.Link.ResponsePacket(p.minecraftUuid, code, false, ""));
					}
				}
				case CommandPackets.Unlink.RequestPacket p -> {
					String unlinkedDiscordId = LinkedAccountManager.unlinkByMinecraftUuid(p.minecraftUuid, p.playerName);
					String discordName = "";
					if (unlinkedDiscordId != null) {
						discordName = DiscordManager.resolveDiscordUserName(unlinkedDiscordId);
					}
					ctx.writeAndFlush(new CommandPackets.Unlink.ResponsePacket(p.minecraftUuid, unlinkedDiscordId != null, discordName));
				}
				case null, default -> logUnexpectedPacket(packet);
			}
		} else {
			switch (packet) {
				case HandshakePacket p -> {
					if ("single_server".equals(ModeManager.getMode())) {
						if (!"Internal".equals(p.serverName)) {
							reject(ctx, p.serverName, "server.network.disconnect_reasons.single_server_mode", p.serverName);
							return;
						}
					} else {
						// Resolve the config entry once: the whitelist check and the Minecraft version check both
						// need it, and every lookup walks the whole multi_server.servers array.
						JsonNode serverConfig = findServerConfig(p.serverName);
						if (serverConfig == null) {
							reject(ctx, p.serverName, "server.network.disconnect_reasons.not_whitelisted", p.serverName);
							return;
						}

						if (NetworkManager.isClientConnected(p.serverName)) {
							reject(ctx, p.serverName, "server.network.disconnect_reasons.duplicate_name", p.serverName);
							return;
						}

						if (!Constants.VERSION.equals(p.dmccVersion)) {
							reject(ctx, p.serverName, "server.network.disconnect_reasons.version_mismatch", "DMCC", p.dmccVersion, Constants.VERSION);
							return;
						}

						String expectedMinecraftVersion = serverConfig.path("minecraft_version").asString();
						if (!expectedMinecraftVersion.equals(p.minecraftVersion)) {
							reject(ctx, p.serverName, "server.network.disconnect_reasons.version_mismatch", "Minecraft", p.minecraftVersion, expectedMinecraftVersion);
							return;
						}
					}

					this.clientName = p.serverName;
					this.expectedNonce = CryptUtils.generateRandomString(16);
					ctx.writeAndFlush(new ChallengePacket(this.expectedNonce));
				}
				case AuthResponsePacket p -> {
					// A nonce only exists after a successful HandshakePacket. Without one the hash would be derived
					// from the literal "null" prefix, which must never count as authentication; the failure counter
					// bounds how many attempts a connection gets before it is dropped for good.
					if (this.expectedNonce == null || this.authFailureCount >= MAX_CONSECUTIVE_AUTH_FAILURES) {
						this.authFailureCount++;
						// Without a handshake no name is known yet, so report the connection as unknown
						reject(ctx, clientName != null ? clientName : "unknown", "server.network.disconnect_reasons.auth_failed");
						return;
					}

					String correctHash = CryptUtils.sha256(this.expectedNonce + server.getSharedSecret());

					if (correctHash.equals(p.hash)) {
						this.authenticated = true;

						NetworkManager.addClientChannel(ctx.channel(), clientName);

						LOGGER.info(I18nManager.getDmccTranslation("server.network.auth_success", clientName));
						boolean consoleForwardingEnabled = isConsoleForwardingEnabledForClient(clientName);
						ctx.writeAndFlush(new LoginSuccessPacket(
								ConfigManager.getString("language"),
								ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages"),
								consoleForwardingEnabled
						));
						if (consoleForwardingEnabled) {
							DiscordManager.sendConsoleForwardingStatusMessage(clientName, true);
						}
						BotPresenceManager.update();
					} else {
						this.authFailureCount++;
						reject(ctx, clientName, "server.network.disconnect_reasons.auth_failed");
					}
				}
				case null, default -> logUnexpectedPacket(packet);
			}
		}
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

	/**
	 * Resolves the reason, logs it, flushes the disconnect packet and closes the channel, in that order.
	 */
	private void reject(ChannelHandlerContext ctx, String serverName, String reasonKey, Object... args) {
		String reason = I18nManager.getDmccTranslation(reasonKey, args);
		LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", serverName, reason));
		ctx.writeAndFlush(new DisconnectPacket(reasonKey, args));
		ctx.close();
	}

	/**
	 * Builds the unexpected-packet message only when there is an unexpected packet to report, instead of on
	 * every inbound packet.
	 */
	private void logUnexpectedPacket(Packet packet) {
		LOGGER.warn(I18nManager.getDmccTranslation("server.network.unexpected_packet", clientName, packet == null ? "null" : packet.getClass().getSimpleName()));
	}

	private JsonNode findServerConfig(String serverName) {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (serverName.equals(node.path("name").asString())) {
					return node;
				}
			}
		}
		return null;
	}

	private void handleMinecraftUserMessage(MinecraftEventPacket packet,
	                                        String sourceClientName,
	                                        String channelNode) {
		String rawContent = packet.placeholders.getOrDefault("message", "");
		String displayName = packet.placeholders.getOrDefault("display_name", packet.placeholders.getOrDefault("player_name", "Unknown"));
		String roleColor = resolveDisplayRoleColor(packet.placeholders.getOrDefault("player_uuid", ""));

		boolean parseForMinecraft = true;
		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseUserMessage(rawContent, parseForMinecraft);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());

		Map<String, String> discordPlaceholders = new HashMap<>(packet.placeholders);
		discordPlaceholders.put("server", sourceClientName);
		discordPlaceholders.put("message", parsed.discordContent());
		DiscordManager.sendMinecraftUserMessage(sourceClientName, channelNode, discordPlaceholders);

		broadcastMinecraftRelay(packet, sourceClientName, relaySegments, overwriteSegments, parsed, true, false, true, channelNode);
	}

	private void handleMinecraftCommandMessage(MinecraftEventPacket packet, String sourceClientName) {
		String command = packet.placeholders.getOrDefault("command", "");
		if (isExcludedMinecraftCommand(command)) {
			return;
		}

		String displayName = packet.placeholders.getOrDefault("display_name", packet.placeholders.getOrDefault("player_name", "Unknown"));
		String roleColor = resolveDisplayRoleColor(packet.placeholders.getOrDefault("player_uuid", ""));
		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseCommandMessage(command);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());

		Map<String, String> discordPlaceholders = new HashMap<>(packet.placeholders);
		discordPlaceholders.put("server", sourceClientName);
		discordPlaceholders.put("message", parsed.discordContent());
		DiscordManager.sendMinecraftUserMessage(sourceClientName, "player.command", discordPlaceholders);

		boolean echoPlayerCommandToSource = ConfigManager.getBoolean("broadcasts.echo_player_command_to_source");
		broadcastMinecraftRelay(packet, sourceClientName, relaySegments, overwriteSegments, parsed, false, echoPlayerCommandToSource, false, "player.command");
	}

	private void handleMinecraftTellRawMessage(MinecraftEventPacket packet, String sourceClientName) {
		String translatedMessage = packet.placeholders.getOrDefault("message", "");
		String componentJson = packet.placeholders.getOrDefault("component_json", "");
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

		broadcastMinecraftTellRawRelay(sourceClientName, relaySegments, overwriteSegments, componentJson, translatedMessage);
	}

	/**
	 * Compiled form of an excluded command regex, at most one compilation per distinct source string.
	 * Compilation stays lazy at the same loop position as the old {@code Pattern.matches}, so an invalid
	 * regex still throws {@link java.util.regex.PatternSyntaxException} there; failures are not cached.
	 */
	private static Pattern excludedCommandPattern(String regex) {
		return EXCLUDED_COMMAND_PATTERNS.computeIfAbsent(regex, Pattern::compile);
	}

	private boolean isExcludedMinecraftCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}

		JsonNode excludedCommands = ConfigManager.getConfigNode("broadcasts.excluded_commands");
		if (excludedCommands.isArray()) {
			for (JsonNode excludedCommand : excludedCommands) {
				if (excludedCommand != null && excludedCommand.isString() && excludedCommandPattern(excludedCommand.asString()).matcher(command).matches()) {
					return true;
				}
			}
		}

		return false;
	}

	private void handleMinecraftSystemMessage(MinecraftEventPacket packet, String sourceClientName, String channelNode,
	                                          String lang, boolean canOverwriteEchoToSource, boolean forceEchoToSource) {
		String message;
		if (packet.type == MinecraftEventPacket.MessageType.SOURCE_ME) {
			String rawAction = packet.placeholders.getOrDefault("action", "");
			String displayName = packet.placeholders.getOrDefault("display_name", packet.placeholders.getOrDefault("player_name", "Unknown"));
			message = (displayName + " " + rawAction).trim();
		} else {
			message = resolveMinecraftToDiscordMessage(lang, packet.placeholders);
		}

		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseSystemMessage(message, true);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildSystemMessageSegments(sourceClientName, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteSystemMessageSegments(sourceClientName, parsed.minecraftSegments());

		Map<String, String> placeholders = new HashMap<>(packet.placeholders);
		if (packet.type == MinecraftEventPacket.MessageType.SOURCE_ME) {
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

	private void broadcastMinecraftRelay(MinecraftEventPacket packet,
	                                     String sourceClientName,
	                                     List<TextSegment> relaySegments,
	                                     List<TextSegment> overwriteSegments,
	                                     MinecraftMessageParser.ParsedMessage parsed,
	                                     boolean canOverwriteEchoToSource,
	                                     boolean forceEchoToSource,
	                                     boolean parseMentionsForNotifications,
	                                     String broadcastNode) {
		boolean overwrite = ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");
		boolean sourceEchoEnabled = (overwrite && canOverwriteEchoToSource) || forceEchoToSource;
		boolean supportMinecraftToMinecraftConfig = "standalone".equals(ModeManager.getMode());
		boolean toOtherClients = supportMinecraftToMinecraftConfig && ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft." + broadcastNode);

		if (!toOtherClients && !sourceEchoEnabled) {
			return;
		}

		boolean notifyMentions = parseMentionsForNotifications
				&& (parsed.mentionEveryone() || !parsed.mentionedPlayerUuids().isEmpty())
				&& ConfigManager.getBoolean("account_linking.mention_notifications.enable");

		String displayName = notifyMentions ? packet.placeholders.getOrDefault("display_name", "Unknown") : null;

		MinecraftRelayPacket relayPacket = newRelayPacket(relaySegments, "", "");
		MinecraftRelayPacket sourcePacket = newRelayPacket(overwriteSegments, "", "");
		if (notifyMentions) {
			applyMentionNotification(relayPacket, displayName, parsed);
			applyMentionNotification(sourcePacket, displayName, parsed);
		}
		dispatchRelay(sourceClientName, toOtherClients, sourceEchoEnabled, relayPacket, sourcePacket);
	}

	private void broadcastMinecraftTellRawRelay(String sourceClientName,
	                                            List<TextSegment> relaySegments,
	                                            List<TextSegment> overwriteSegments,
	                                            String componentJson,
	                                            String componentText) {
		boolean sourceEchoEnabled = ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");
		boolean supportMinecraftToMinecraftConfig = "standalone".equals(ModeManager.getMode());
		boolean toOtherClients = supportMinecraftToMinecraftConfig && ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft.source.tell_raw");

		if (!toOtherClients && !sourceEchoEnabled) {
			return;
		}

		dispatchRelay(sourceClientName, toOtherClients, sourceEchoEnabled,
				newRelayPacket(relaySegments, componentJson, componentText),
				newRelayPacket(overwriteSegments, componentJson, componentText));
	}

	private static MinecraftRelayPacket newRelayPacket(List<TextSegment> segments, String componentJson, String componentText) {
		MinecraftRelayPacket packet = new MinecraftRelayPacket(segments);
		if (!componentJson.isBlank()) {
			packet.componentJson = componentJson;
			packet.componentPlaceholder = TELLRAW_COMPONENT_PLACEHOLDER;
			packet.componentText = componentText;
		}
		return packet;
	}

	private static void dispatchRelay(String sourceClientName, boolean toOtherClients, boolean sourceEchoEnabled,
	                                  MinecraftRelayPacket relayPacket, MinecraftRelayPacket sourcePacket) {
		if (toOtherClients) {
			NetworkManager.broadcastToClientsExcept(relayPacket, sourceClientName);
		}
		if (sourceEchoEnabled) {
			NetworkManager.sendPacketToClient(sourcePacket, sourceClientName);
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
		return DiscordMessageParser.getRoleColorHex(DiscordManager.retrieveMember(discordId));
	}

	private boolean isConsoleForwardingEnabledForClient(String serverName) {
		if (!ConfigManager.getBoolean("console_forwarding.enable")) {
			return false;
		}

		if ("standalone".equals(ModeManager.getMode())) {
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
}
