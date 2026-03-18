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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
public class ServerHandler extends SimpleChannelInboundHandler<Packet> {

	// Minecraft-side chat does not carry Discord role data, so relay uses a neutral fallback.
	private static final String RELAY_DEFAULT_ROLE_COLOR = "white";
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
							DiscordManager.clientBroadcast(clientName, "server.started", "server.start", false, p.placeholders);

							// After a Minecraft server starts, perform OP level sync if enabled
							OpSyncManager.syncAll();
							DiscordManager.updateBotPresence();
						}
						case SERVER_STOPPING ->
								DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", false, p.placeholders);
						// Player events
						case PLAYER_JOIN -> {
							DiscordManager.clientBroadcast(clientName, "player.join", "player.join", false, p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_QUIT -> {
							DiscordManager.clientBroadcast(clientName, "player.quit", "player.quit", false, p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_DIE ->
								DiscordManager.clientBroadcast(clientName, "player.die", "player.die", false, p.placeholders);
						case PLAYER_ADVANCEMENT ->
								DiscordManager.clientBroadcast(clientName, "player.advancement", "player.advancement." + p.placeholders.get("type"), false, p.placeholders);
						case PLAYER_CHAT -> {
							DiscordManager.clientBroadcast(clientName, "player.chat", "player.chat", true, p.placeholders);
							relayToOtherClients(clientName, true, p.placeholders);
						}
						case PLAYER_COMMAND -> {
							String command = p.placeholders.getOrDefault("message", p.placeholders.getOrDefault("command", ""));
							if (!isExcludedCommand(command)) {
								DiscordManager.clientBroadcast(clientName, "player.command", "player.command", true, p.placeholders);
								relayToOtherClients(clientName, true, p.placeholders);
							}
						}
						case SOURCE_SAY -> {
							DiscordManager.clientBroadcast(clientName, "source.say", "source.say", true, p.placeholders);
							relayToOtherClients(clientName, false, p.placeholders);
						}
						case SOURCE_TELL_RAW -> {
							DiscordManager.clientBroadcast(clientName, "source.tell_raw", "source.tell_raw", true, p.placeholders);
							relayToOtherClients(clientName, false, p.placeholders);
						}
						case SERVER_STOPPED ->
								DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", false, p.placeholders);
						default ->
								LOGGER.warn("Received MinecraftEventPacket from authenticated client {}: type={}, placeholders={}", clientName, p.type, p.placeholders);
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
						ctx.writeAndFlush(new LoginSuccessPacket(ConfigManager.getString("language")));

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

	/**
	 * Relays a Minecraft-originated message to all connected clients except the source server.
	 * The server pre-builds rich segments so clients can render directly without custom_messages access.
	 *
	 * @param sourceClientName Source server name.
	 * @param isChat           Whether to use common.chat (true) or common.others (false).
	 * @param placeholders     Event placeholders.
	 */
	private void relayToOtherClients(String sourceClientName, boolean isChat, Map<String, String> placeholders) {
		if ("single_server".equals(ModeManager.getMode())) {
			return;
		}
		if (placeholders == null || placeholders.isEmpty()) {
			return;
		}

		String message = placeholders.getOrDefault("message", placeholders.getOrDefault("command", ""));
		if (message == null || message.isBlank()) {
			return;
		}

		List<TextSegment> segments = buildRelaySegments(sourceClientName, isChat, placeholders);
		if (segments.isEmpty()) {
			return;
		}

		DiscordEventPacket relayPacket = new DiscordEventPacket(DiscordEventPacket.EventType.CHAT, segments);
		NetworkManager.broadcastToClientsExcept(relayPacket, sourceClientName);
	}

	/**
	 * Builds relay segments from custom_messages common templates.
	 *
	 * @param sourceClientName Source server name.
	 * @param isChat           true for common.chat, false for common.others.
	 * @param placeholders     Event placeholders.
	 * @return Relay segments.
	 */
	private List<TextSegment> buildRelaySegments(String sourceClientName, boolean isChat, Map<String, String> placeholders) {
		List<TextSegment> segments = new ArrayList<>();
		JsonNode root = I18nManager.getCustomMessages();
		if (root == null) {
			return segments;
		}

		JsonNode node = root.path("common").path(isChat ? "chat" : "others");
		if (!node.isArray()) {
			return segments;
		}

		String serverColor = getClientColor(sourceClientName);

		for (JsonNode segNode : node) {
			String text = segNode.path("text").asText("");
			boolean bold = segNode.path("bold").asBoolean(false);
			String color = segNode.path("color").asText("");

			text = applyRelayPlaceholders(text, sourceClientName, serverColor, placeholders);
			color = applyRelayPlaceholders(color, sourceClientName, serverColor, placeholders);

			segments.add(new TextSegment(text, bold, color));
		}

		return segments;
	}

	/**
	 * Applies relay placeholders for cross-server messages.
	 *
	 * @param input            Template text/color.
	 * @param sourceClientName Source server name.
	 * @param serverColor      Source server color.
	 * @param placeholders     Event placeholders.
	 * @return Resolved text.
	 */
	private String applyRelayPlaceholders(String input, String sourceClientName, String serverColor, Map<String, String> placeholders) {
		String value = input.replace("{server}", sourceClientName)
				.replace("{server_color}", serverColor)
				.replace("{role_color}", RELAY_DEFAULT_ROLE_COLOR)
				.replace("{effective_name}", placeholders.getOrDefault("display_name", placeholders.getOrDefault("user_name", "")));

		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			value = value.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return value;
	}

	/**
	 * Gets configured color for a client in standalone mode.
	 *
	 * @param serverName Server name.
	 * @return Color name or fallback value.
	 */
	private String getClientColor(String serverName) {
		JsonNode config = findServerConfig(serverName);
		if (config != null) {
			String color = config.path("color").asText();
			if (color != null && !color.isBlank()) {
				return color;
			}
		}
		return "white";
	}

	/**
	 * Checks whether a player command should be excluded from broadcasting.
	 *
	 * @param command Command text.
	 * @return true if excluded.
	 */
	private boolean isExcludedCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}

		JsonNode node = ConfigManager.getConfigNode("excluded_commands");
		if (!node.isArray()) {
			return false;
		}

		for (JsonNode patternNode : node) {
			String regex = patternNode.asText("");
			if (regex.isBlank()) {
				continue;
			}
			try {
				if (Pattern.compile(regex).matcher(command).matches()) {
					return true;
				}
			} catch (Exception e) {
				LOGGER.warn("Invalid excluded_commands regex ignored: {}", regex, e);
			}
		}

		return false;
	}
}
