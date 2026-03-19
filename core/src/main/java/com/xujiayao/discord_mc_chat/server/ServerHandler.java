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
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.misc.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.LatencyPingPacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.LatencyPongPacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.OpSyncManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.List;
import java.util.Map;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
public class ServerHandler extends SimpleChannelInboundHandler<Packet> {

	private final NettyServer server;
	private String expectedNonce;
	private boolean authenticated = false;
	private String clientName;

	public ServerHandler(NettyServer server) {
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
							broadcastMinecraftToMinecraftEvent(clientName, "server.started",
									buildSystemPlaceholders(clientName, "server.start", p.placeholders), false, true, false);

							// After a Minecraft server starts, perform OP level sync if enabled
							OpSyncManager.syncAll();
							DiscordManager.updateBotPresence();
						}
						case SERVER_STOPPING -> {
							DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "server.stopped",
									buildSystemPlaceholders(clientName, "server.stop", p.placeholders), false, true, false);
						}
						// Player events
						case PLAYER_JOIN -> {
							DiscordManager.clientBroadcast(clientName, "player.join", "player.join", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "player.join",
									buildSystemPlaceholders(clientName, "player.join", p.placeholders), false, true, false);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_QUIT -> {
							DiscordManager.clientBroadcast(clientName, "player.quit", "player.quit", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "player.quit",
									buildSystemPlaceholders(clientName, "player.quit", p.placeholders), false, true, false);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_DIE -> {
							DiscordManager.clientBroadcast(clientName, "player.die", "player.die", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "player.die",
									buildSystemPlaceholders(clientName, "player.die", p.placeholders), false, true, false);
						}
						case PLAYER_ADVANCEMENT -> {
							String type = p.placeholders.get("type");
							DiscordManager.clientBroadcast(clientName, "player.advancement", "player.advancement." + type, p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "player.advancement",
									buildSystemPlaceholders(clientName, "player.advancement." + type, p.placeholders), false, true, false);
						}
						case PLAYER_CHANGE_GAME_MODE -> {
							DiscordManager.clientBroadcast(clientName, "player.change_game_mode", "player.change_game_mode", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "player.change_game_mode",
									buildSystemPlaceholders(clientName, "player.change_game_mode", p.placeholders), false, true, false);
						}
						case PLAYER_CHAT -> {
							DiscordManager.clientBroadcastUserMessage(clientName, "player.chat",
									p.placeholders.getOrDefault("display_name", p.placeholders.getOrDefault("user_name", "unknown")),
									p.placeholders.getOrDefault("message", ""));
							broadcastMinecraftToMinecraftEvent(clientName, "player.chat", p.placeholders, true, true, true);
						}
						case PLAYER_COMMAND -> {
							DiscordManager.clientBroadcastUserMessage(clientName, "player.command",
									p.placeholders.getOrDefault("display_name", p.placeholders.getOrDefault("user_name", "unknown")),
									p.placeholders.getOrDefault("message", ""));
							broadcastMinecraftToMinecraftEvent(clientName, "player.command", p.placeholders, true, false, true);
						}
						case SOURCE_SAY -> {
							DiscordManager.clientBroadcastUserMessage(clientName, "source.say",
									p.placeholders.getOrDefault("display_name", p.placeholders.getOrDefault("user_name", "unknown")),
									p.placeholders.getOrDefault("message", ""));
							broadcastMinecraftToMinecraftEvent(clientName, "source.say", p.placeholders, true, true, true);
						}
						case SOURCE_TELL_RAW -> {
							DiscordManager.clientBroadcastUserMessage(clientName, "source.tell_raw",
									p.placeholders.getOrDefault("display_name", p.placeholders.getOrDefault("user_name", "unknown")),
									p.placeholders.getOrDefault("message", ""));
							broadcastMinecraftToMinecraftEvent(clientName, "source.tell_raw", p.placeholders, true, true, true);
						}
						case SOURCE_MSG -> {
							DiscordManager.clientBroadcastUserMessage(clientName, "source.msg",
									p.placeholders.getOrDefault("display_name", p.placeholders.getOrDefault("user_name", "unknown")),
									p.placeholders.getOrDefault("message", ""));
							broadcastMinecraftToMinecraftEvent(clientName, "source.msg", p.placeholders, true, true, true);
						}
						case SOURCE_ME -> {
							DiscordManager.clientBroadcast(clientName, "source.me", "source.me", p.placeholders);
							broadcastMinecraftToMinecraftEvent(clientName, "source.me", p.placeholders, false, true, true);
						}
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

	private Map<String, String> buildSystemPlaceholders(String clientName, String messageKey, Map<String, String> placeholders) {
		String message = renderMinecraftToXxxxx(messageKey, placeholders);
		return Map.of(
				"display_name", clientName,
				"message", message
		);
	}

	private String renderMinecraftToXxxxx(String messageKey, Map<String, String> placeholders) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return "";
		}
		String[] parts = ("minecraft_to_xxxxx." + messageKey).split("\\.");
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

	private void broadcastMinecraftToMinecraftEvent(String sourceClientName,
													String channelNode,
													Map<String, String> placeholders,
													boolean userMessage,
													boolean parseRichContent,
													boolean sourceMessageType) {
		if (!isMinecraftToMinecraftEnabled(channelNode)) {
			return;
		}

		String effectiveName = placeholders.getOrDefault("display_name", placeholders.getOrDefault("user_name", "unknown"));
		String message = placeholders.getOrDefault("message", "");

		List<TextSegment> segments;
		boolean useSingleServerOverwrite = "single_server".equals(ModeManager.getMode())
				&& ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");

		if (useSingleServerOverwrite) {
			segments = DiscordMessageParser.buildSingleServerOverwriteSegments(
					effectiveName,
					"white",
					message,
					userMessage,
					parseRichContent
			);
		} else {
			segments = DiscordMessageParser.buildMinecraftToMinecraftSegments(
					effectiveName,
					"white",
					message,
					userMessage,
					parseRichContent
			);
		}

		DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.CHAT, segments);
		boolean includeSource = sourceMessageType && ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");
		if (includeSource) {
			NetworkManager.broadcastToClients(packet);
		} else {
			NetworkManager.broadcastToClientsExcept(packet, sourceClientName);
		}
	}

	private boolean isMinecraftToMinecraftEnabled(String channelNode) {
		String key = "broadcasts.minecraft_to_minecraft." + channelNode;
		JsonNode node = ConfigManager.getConfigNode(key);
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return Boolean.TRUE.equals(ConfigManager.getBoolean("broadcasts.between_minecraft_servers." + channelNode));
	}
}
