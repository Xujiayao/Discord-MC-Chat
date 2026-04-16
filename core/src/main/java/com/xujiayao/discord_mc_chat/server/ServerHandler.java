package com.xujiayao.discord_mc_chat.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.impl.ConsoleCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
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
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.message.TextSegment;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

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

	private final NettyServer server;
	private String expectedNonce;
	private boolean authenticated = false;
	private boolean initialOpSyncDone = false;
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
			DiscordManager.sendConsoleForwardingStatusMessage(clientName, false);
		}
		// Clean up from NetworkManager
		NetworkManager.removeClientChannel(ctx.channel());
		BotPresenceManager.update();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof KeepAlivePacket) {
			// No-op, just resets idle timer
			return;
		}

		String unexpectedPacketMessage = I18nManager.getDmccTranslation("server.network.unexpected_packet", clientName, packet == null ? "null" : packet.getClass().getSimpleName());

		if (authenticated) {
			switch (packet) {
				case ConsoleLogBatchPacket p -> DiscordManager.sendConsoleForwardedBatchMessage(clientName, p.lines);
				case MinecraftEventPacket p -> {
					switch (p.type) {
						// Server events
						case SERVER_STARTED -> {
							handleMinecraftSystemMessage(p, clientName, "server.started", "server.start");
							BotPresenceManager.update();
						}
						case SERVER_STOPPING -> {
							handleMinecraftSystemMessage(p, clientName, "server.stopped", "server.stop");
							if ("single_server".equals(ModeManager.getMode())) {
								ChannelUpdateManager.updateOfflineForSingleServerShutdownAndWait();
							}
							ctx.close();
						}
						// Player events
						case PLAYER_JOIN -> {
							handleMinecraftSystemMessage(p, clientName, "player.join", "player.join");

							// Perform the initial OP sync once, on the first player join for this DMCC client.
							if (!initialOpSyncDone) {
								initialOpSyncDone = true;
								OpSyncManager.syncAll();
							}

							BotPresenceManager.update();
						}
						case PLAYER_QUIT -> {
							handleMinecraftSystemMessage(p, clientName, "player.quit", "player.quit");
							BotPresenceManager.update();
						}
						case PLAYER_DIE -> handleMinecraftSystemMessage(p, clientName, "player.die", "player.die");
						case PLAYER_ADVANCEMENT ->
								handleMinecraftSystemMessage(p, clientName, "player.advancement", "player.advancement." + p.placeholders.getOrDefault("type", ""));
						case PLAYER_CHANGE_GAME_MODE ->
								handleMinecraftSystemMessage(p, clientName, "player.change_game_mode", "player.change_game_mode");
						case PLAYER_CHAT -> handleMinecraftUserMessage(p, clientName, "player.chat");
						case PLAYER_COMMAND -> handleMinecraftCommandMessage(p, clientName);
						case SOURCE_SAY -> handleMinecraftUserMessage(p, clientName, "source.say");
						case SOURCE_TELL_RAW -> handleMinecraftTellRawMessage(p, clientName);
						case SOURCE_MSG -> handleMinecraftUserMessage(p, clientName, "source.msg");
						case SOURCE_ME -> handleMinecraftSystemMessage(p, clientName, "source.me", "source.me");
					}
				}
				case CommandPackets.Info.ResponsePacket p -> NetworkManager.cacheInfoResponse(clientName, p);
				case LatencyPingPacket p -> ctx.writeAndFlush(new LatencyPongPacket(p.sentAtMillis));
				case CommandPackets.Execute.ResponsePacket p -> ExecuteCommand.completeRequest(p.requestId, p);
				case CommandPackets.Console.ResponsePacket p -> ConsoleCommand.completeRequest(p.requestId, p);
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
				case null, default -> LOGGER.warn(unexpectedPacketMessage);
			}
		} else {
			switch (packet) {
				case HandshakePacket p -> {
					if ("single_server".equals(ModeManager.getMode())) {
						if (!"Internal".equals(p.serverName)) {
							String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.single_server_mode", p.serverName);
							LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", p.serverName, reason));
							ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.single_server_mode", p.serverName));
							ctx.close();
							return;
						}
					} else {
						if (!isWhitelisted(p.serverName)) {
							String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.not_whitelisted", p.serverName);
							LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", p.serverName, reason));
							ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.not_whitelisted", p.serverName));
							ctx.close();
							return;
						}

						if (NetworkManager.isClientConnected(p.serverName)) {
							String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.duplicate_name", p.serverName);
							LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", p.serverName, reason));
							ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.duplicate_name", p.serverName));
							ctx.close();
							return;
						}

						if (!Constants.VERSION.equals(p.dmccVersion)) {
							String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.version_mismatch", "DMCC", p.dmccVersion, Constants.VERSION);
							LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", p.serverName, reason));
							ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.version_mismatch", "DMCC", p.dmccVersion, Constants.VERSION));
							ctx.close();
							return;
						}

						if (!getMinecraftVersion(p.serverName).equals(p.minecraftVersion)) {
							String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.version_mismatch", "Minecraft", p.minecraftVersion, getMinecraftVersion(p.serverName));
							LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", p.serverName, reason));
							ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.version_mismatch", "Minecraft", p.minecraftVersion, getMinecraftVersion(p.serverName)));
							ctx.close();
							return;
						}
					}

					this.clientName = p.serverName;
					this.expectedNonce = CryptUtils.generateRandomString(16);
					ctx.writeAndFlush(new ChallengePacket(this.expectedNonce));
				}
				case AuthResponsePacket p -> {
					String correctHash = CryptUtils.sha256(this.expectedNonce + server.getSharedSecret());

					if (correctHash.equals(p.hash)) {
						this.authenticated = true;

						// Register client in NetworkManager
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
						String reason = I18nManager.getDmccTranslation("server.network.disconnect_reasons.auth_failed");
						LOGGER.error(I18nManager.getDmccTranslation("server.network.reject", clientName, reason));
						ctx.writeAndFlush(new DisconnectPacket("server.network.disconnect_reasons.auth_failed"));
						ctx.close();
					}
				}
				case null, default -> LOGGER.warn(unexpectedPacketMessage);
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

	private JsonNode findServerConfig(String serverName) {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (serverName.equals(node.path("name").asText())) {
					return node;
				}
			}
		}
		return null;
	}

	private boolean isWhitelisted(String serverName) {
		return findServerConfig(serverName) != null;
	}

	private String getMinecraftVersion(String serverName) {
		JsonNode config = findServerConfig(serverName);
		return config != null ? config.path("minecraft_version").asText() : "";
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

		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.USER_MESSAGE, relaySegments, overwriteSegments, parsed, true, false, true, channelNode);
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
		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.COMMAND, relaySegments, overwriteSegments, parsed, false, echoPlayerCommandToSource, false, "player.command");
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

		broadcastMinecraftTellRawRelay(sourceClientName, relaySegments, overwriteSegments, componentJson, translatedMessage, useSerializedComponent);
	}

	private boolean isExcludedMinecraftCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}

		JsonNode excludedCommands = ConfigManager.getConfigNode("excluded_commands");
		if (excludedCommands.isArray()) {
			for (JsonNode excludedCommand : excludedCommands) {
				if (excludedCommand != null && excludedCommand.isTextual() && Pattern.matches(excludedCommand.asText(), command)) {
					return true;
				}
			}
		}

		return false;
	}

	private void handleMinecraftSystemMessage(MinecraftEventPacket packet, String sourceClientName, String channelNode, String lang) {
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

		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.SYSTEM_MESSAGE, relaySegments, overwriteSegments, parsed, true, false, true, channelNode);
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

		String message = messageNode.asText("");
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return message;
	}

	private void broadcastMinecraftRelay(MinecraftEventPacket packet,
	                                     String sourceClientName,
	                                     MinecraftRelayPacket.MessageType relayType,
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

		if (toOtherClients) {
			MinecraftRelayPacket relayPacket = new MinecraftRelayPacket(relayType, relaySegments);
			if (notifyMentions) {
				applyMentionNotification(relayPacket, displayName, parsed);
			}
			NetworkManager.broadcastToClientsExcept(relayPacket, sourceClientName);
		}

		if (sourceEchoEnabled) {
			MinecraftRelayPacket sourcePacket = new MinecraftRelayPacket(relayType, overwriteSegments);
			if (notifyMentions) {
				applyMentionNotification(sourcePacket, displayName, parsed);
			}
			NetworkManager.sendPacketToClient(sourcePacket, sourceClientName);
		}
	}

	private void broadcastMinecraftTellRawRelay(String sourceClientName,
	                                            List<TextSegment> relaySegments,
	                                            List<TextSegment> overwriteSegments,
	                                            String componentJson,
	                                            String componentText,
	                                            boolean useSerializedComponent) {
		boolean sourceEchoEnabled = ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");
		boolean supportMinecraftToMinecraftConfig = "standalone".equals(ModeManager.getMode());
		boolean toOtherClients = supportMinecraftToMinecraftConfig && ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft." + "source.tell_raw");

		if (!toOtherClients && !sourceEchoEnabled) {
			return;
		}

		if (toOtherClients) {
			MinecraftRelayPacket relayPacket = new MinecraftRelayPacket(MinecraftRelayPacket.MessageType.SYSTEM_MESSAGE, relaySegments);
			if (useSerializedComponent) {
				relayPacket.componentJson = componentJson;
				relayPacket.componentPlaceholder = TELLRAW_COMPONENT_PLACEHOLDER;
				relayPacket.componentText = componentText;
			}
			NetworkManager.broadcastToClientsExcept(relayPacket, sourceClientName);
		}

		if (sourceEchoEnabled) {
			MinecraftRelayPacket sourcePacket = new MinecraftRelayPacket(MinecraftRelayPacket.MessageType.SYSTEM_MESSAGE, overwriteSegments);
			if (useSerializedComponent) {
				sourcePacket.componentJson = componentJson;
				sourcePacket.componentPlaceholder = TELLRAW_COMPONENT_PLACEHOLDER;
				sourcePacket.componentText = componentText;
			}
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
				if (serverName.equals(node.path("server").asText("")) && !node.path("channel").asText("").isBlank()) {
					return true;
				}
			}
			return false;
		}

		return !ConfigManager.getString("console_forwarding.channel", "").isBlank();
	}
}
