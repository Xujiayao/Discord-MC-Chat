package com.xujiayao.discord_mc_chat.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.impl.ConsoleCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.network.packets.auth.AuthResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.ChallengePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.DisconnectPacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.HandshakePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.LoginSuccessPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleAutoCompleteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteAutoCompleteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.unlink.UnlinkRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.unlink.UnlinkResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftRelayPacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.LatencyPingPacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.LatencyPongPacket;
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

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
final class ServerHandler extends SimpleChannelInboundHandler<Packet> {

	private final NettyServer server;
	private String expectedNonce;
	private boolean authenticated = false;
	private String clientName;

	ServerHandler(NettyServer server) {
		this.server = server;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		// Wait for handshake
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		if (clientName != null) {
			LOGGER.warn(I18nManager.getDmccTranslation("server.network.client_disconnected_normal", clientName));
		}
		// Clean up from NetworkManager
		NetworkManager.removeClientChannel(ctx.channel());
		DiscordManager.updateBotPresence();
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
				case MinecraftEventPacket p -> {
					switch (p.type) {
						// Server events
						case SERVER_STARTED -> {
							DiscordManager.clientBroadcast(clientName, "server.started", "server.start", p.placeholders);

							// After a Minecraft server starts, perform OP level sync if enabled
							OpSyncManager.syncAll();
							DiscordManager.updateBotPresence();
						}
						case SERVER_STOPPING ->
								DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", p.placeholders);
						// Player events
						case PLAYER_JOIN -> {
							DiscordManager.clientBroadcast(clientName, "player.join", "player.join", p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_QUIT -> {
							DiscordManager.clientBroadcast(clientName, "player.quit", "player.quit", p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_DIE ->
								DiscordManager.clientBroadcast(clientName, "player.die", "player.die", p.placeholders);
						case PLAYER_ADVANCEMENT ->
								DiscordManager.clientBroadcast(clientName, "player.advancement", "player.advancement." + p.placeholders.get("type"), p.placeholders);
						case PLAYER_CHANGE_GAME_MODE ->
								DiscordManager.clientBroadcast(clientName, "player.change_game_mode", "player.change_game_mode", p.placeholders);
						case PLAYER_CHAT -> handleMinecraftUserMessage(p, clientName, "player.chat");
						case PLAYER_COMMAND -> handleMinecraftCommandMessage(p, clientName);
						case SOURCE_SAY -> handleMinecraftUserMessage(p, clientName, "source.say");
						case SOURCE_MSG -> handleMinecraftUserMessage(p, clientName, "source.msg");
						case SOURCE_ME -> handleMinecraftSystemMessage(p, clientName);
						// TODO Unhandled events
					}
				}
				case InfoResponsePacket p -> NetworkManager.cacheInfoResponse(clientName, p);
				case LatencyPingPacket p -> ctx.writeAndFlush(new LatencyPongPacket(p.sentAtMillis));
				case ExecuteResponsePacket p -> ExecuteCommand.completeRequest(p.requestId, p);
				case ConsoleResponsePacket p -> ConsoleCommand.completeRequest(p.requestId, p);
				case ExecuteAutoCompleteResponsePacket p ->
						NetworkManager.cacheExecuteAutoCompleteResponse(clientName, p.suggestions);
				case ConsoleAutoCompleteResponsePacket p ->
						NetworkManager.cacheConsoleAutoCompleteResponse(clientName, p.suggestions);
				case LinkRequestPacket p -> {
					if (LinkedAccountManager.isMinecraftUuidLinked(p.minecraftUuid)) {
						if (!p.joinCheck) {
							// Only notify "already linked" for explicit /dmcc link commands, not join checks
							String discordId = LinkedAccountManager.getDiscordIdByMinecraftUuid(p.minecraftUuid);
							String discordName = DiscordManager.resolveDiscordUserName(discordId != null ? discordId : "");
							ctx.writeAndFlush(new LinkResponsePacket(p.minecraftUuid, null, true, discordName));
						}
					} else {
						String code = VerificationCodeManager.generateOrRefreshCode(p.minecraftUuid, p.playerName);
						ctx.writeAndFlush(new LinkResponsePacket(p.minecraftUuid, code, false, ""));
					}
				}
				case UnlinkRequestPacket p -> {
					String unlinkedDiscordId = LinkedAccountManager.unlinkByMinecraftUuid(p.minecraftUuid, p.playerName);
					String discordName = "";
					if (unlinkedDiscordId != null) {
						discordName = DiscordManager.resolveDiscordUserName(unlinkedDiscordId);
					}
					ctx.writeAndFlush(new UnlinkResponsePacket(p.minecraftUuid, unlinkedDiscordId != null, discordName));
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
						ctx.writeAndFlush(new LoginSuccessPacket(ConfigManager.getString("language"), ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages")));

						// After successful authentication, perform OP level sync if enabled
						OpSyncManager.syncAll();
						DiscordManager.updateBotPresence();
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

	/**
	 * Finds the configuration node for the given server name from the multi-server servers list.
	 *
	 * @param serverName The server name to look up.
	 * @return The matching JsonNode, or null if not found.
	 */
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

	/**
	 * Checks if the given server name is whitelisted in the configuration.
	 *
	 * @param serverName The server name to check.
	 * @return true if whitelisted, false otherwise.
	 */
	private boolean isWhitelisted(String serverName) {
		return findServerConfig(serverName) != null;
	}

	/**
	 * Gets the expected Minecraft version for a given server name from the configuration.
	 *
	 * @param serverName The server name to look up.
	 * @return The expected Minecraft version, or an empty string if not found.
	 */
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

		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.USER_MESSAGE, relaySegments, overwriteSegments, parsed, true, true, channelNode);
	}

	private void handleMinecraftCommandMessage(MinecraftEventPacket packet, String sourceClientName) {
		String command = packet.placeholders.getOrDefault("command", "");
		String displayName = packet.placeholders.getOrDefault("display_name", packet.placeholders.getOrDefault("player_name", "Unknown"));
		String roleColor = resolveDisplayRoleColor(packet.placeholders.getOrDefault("player_uuid", ""));
		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseCommandMessage(command);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteUserMessageSegments(sourceClientName, displayName, roleColor, parsed.minecraftSegments());

		Map<String, String> discordPlaceholders = new HashMap<>(packet.placeholders);
		discordPlaceholders.put("server", sourceClientName);
		discordPlaceholders.put("message", parsed.discordContent());
		DiscordManager.sendMinecraftUserMessage(sourceClientName, "player.command", discordPlaceholders);

		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.COMMAND, relaySegments, overwriteSegments, parsed, false, false, "player.command");
	}

	private void handleMinecraftSystemMessage(MinecraftEventPacket packet, String sourceClientName) {
		String rawAction = packet.placeholders.getOrDefault("action", "");
		String displayName = packet.placeholders.getOrDefault("display_name", packet.placeholders.getOrDefault("player_name", "Unknown"));
		String combined = (displayName + " " + rawAction).trim();

		MinecraftMessageParser.ParsedMessage parsed = MinecraftMessageParser.parseSystemMessage(combined, true);
		List<TextSegment> relaySegments = MinecraftMessageParser.buildSystemMessageSegments(sourceClientName, parsed.minecraftSegments());
		List<TextSegment> overwriteSegments = MinecraftMessageParser.buildOverwriteSystemMessageSegments(sourceClientName, parsed.minecraftSegments());

		Map<String, String> placeholders = new HashMap<>(packet.placeholders);
		placeholders.put("action", parsed.discordContent());
		DiscordManager.clientBroadcast(sourceClientName, "source.me", "source.me", placeholders);

		broadcastMinecraftRelay(packet, sourceClientName, MinecraftRelayPacket.MessageType.SYSTEM_MESSAGE, relaySegments, overwriteSegments, parsed, true, true, "source.me");
	}

	private void broadcastMinecraftRelay(MinecraftEventPacket packet,
										 String sourceClientName,
										 MinecraftRelayPacket.MessageType relayType,
										 List<TextSegment> relaySegments,
										 List<TextSegment> overwriteSegments,
										 MinecraftMessageParser.ParsedMessage parsed,
										 boolean canOverwriteEchoToSource,
										 boolean parseMentionsForNotifications,
										 String broadcastNode) {
		boolean overwrite = Boolean.TRUE.equals(ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages"));
		boolean supportMinecraftToMinecraftConfig = "standalone".equals(ModeManager.getMode());
		boolean toOtherClients = supportMinecraftToMinecraftConfig && Boolean.TRUE.equals(ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft." + broadcastNode));

		if (!toOtherClients && !(overwrite && canOverwriteEchoToSource)) {
			return;
		}

		boolean notifyMentions = parseMentionsForNotifications
				&& (parsed.mentionEveryone() || !parsed.mentionedPlayerUuids().isEmpty())
				&& Boolean.TRUE.equals(ConfigManager.getBoolean("account_linking.mention_notifications.enable"));

		if (toOtherClients) {
			MinecraftRelayPacket relayPacket = new MinecraftRelayPacket(relayType, relaySegments);
			if (notifyMentions) {
				relayPacket.mentionNotificationText = MinecraftMessageParser.getMentionNotificationText(packet.placeholders.getOrDefault("display_name", "Unknown"));
				relayPacket.mentionNotificationStyle = ConfigManager.getString("account_linking.mention_notifications.style", "title");
				relayPacket.mentionedPlayerUuids = List.copyOf(parsed.mentionedPlayerUuids());
				relayPacket.mentionEveryone = parsed.mentionEveryone();
			}
			NetworkManager.broadcastToClientsExcept(relayPacket, sourceClientName);
		}

		if (overwrite && canOverwriteEchoToSource) {
			MinecraftRelayPacket sourcePacket = new MinecraftRelayPacket(relayType, overwriteSegments);
			if (notifyMentions) {
				sourcePacket.mentionNotificationText = MinecraftMessageParser.getMentionNotificationText(packet.placeholders.getOrDefault("display_name", "Unknown"));
				sourcePacket.mentionNotificationStyle = ConfigManager.getString("account_linking.mention_notifications.style", "title");
				sourcePacket.mentionedPlayerUuids = List.copyOf(parsed.mentionedPlayerUuids());
				sourcePacket.mentionEveryone = parsed.mentionEveryone();
			}
			NetworkManager.sendPacketToClient(sourcePacket, sourceClientName);
		}
	}

	private String resolveDisplayRoleColor(String playerUuid) {
		if (!Boolean.TRUE.equals(ConfigManager.getBoolean("account_linking.use_discord_role_color_for_mc_chats"))) {
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
}
