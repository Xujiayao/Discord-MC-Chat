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
import com.xujiayao.discord_mc_chat.server.discord.DiscordMessageParser;
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
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);

							// After a Minecraft server starts, perform OP level sync if enabled
							OpSyncManager.syncAll();
							DiscordManager.updateBotPresence();
						}
						case SERVER_STOPPING -> {
							DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						// Player events
						case PLAYER_JOIN -> {
							DiscordManager.clientBroadcast(clientName, "player.join", "player.join", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_QUIT -> {
							DiscordManager.clientBroadcast(clientName, "player.quit", "player.quit", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_CHAT -> {
							DiscordManager.clientBroadcast(clientName, "player.chat", "player.chat", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case PLAYER_COMMAND -> {
							DiscordManager.clientBroadcast(clientName, "player.command", "player.command", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case PLAYER_DIE -> {
							DiscordManager.clientBroadcast(clientName, "player.die", "player.die", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case PLAYER_ADVANCEMENT -> {
							DiscordManager.clientBroadcast(clientName, "player.advancement", "player.advancement." + p.placeholders.get("type"), p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case PLAYER_CHANGE_GAME_MODE -> {
							DiscordManager.clientBroadcast(clientName, "player.change_game_mode", "player.change_game_mode", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						// Source events
						case SOURCE_SAY -> {
							DiscordManager.clientBroadcast(clientName, "source.say", "source.say", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case SOURCE_TELL_RAW -> {
							DiscordManager.clientBroadcast(clientName, "source.tell_raw", "source.tell_raw", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case SOURCE_MSG -> {
							DiscordManager.clientBroadcast(clientName, "source.msg", "source.msg", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
						}
						case SOURCE_ME -> {
							DiscordManager.clientBroadcast(clientName, "source.me", "source.me", p.placeholders);
							broadcastMinecraftEventToMinecraft(clientName, p.type, p.placeholders);
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

	private String getServerColor(String serverName) {
		JsonNode config = findServerConfig(serverName);
		if (config != null) {
			String color = config.path("color").asText();
			if (color != null && !color.isBlank()) {
				return color;
			}
		}
		return "white";
	}

	private void broadcastMinecraftEventToMinecraft(String sourceServerName,
													MinecraftEventPacket.MessageType type,
													Map<String, String> placeholders) {
		boolean singleServerOverwrite = "single_server".equals(ModeManager.getMode())
				&& ConfigManager.getBoolean("message_parsing.overwrite_minecraft_source_messages");

		String eventNode = switch (type) {
			case SERVER_STARTED -> "server.started";
			case SERVER_STOPPING -> "server.stopped";
			case PLAYER_JOIN -> "player.join";
			case PLAYER_QUIT -> "player.quit";
			case PLAYER_CHAT -> "player.chat";
			case PLAYER_COMMAND -> "player.command";
			case PLAYER_DIE -> "player.die";
			case PLAYER_ADVANCEMENT -> "player.advancement";
			case PLAYER_CHANGE_GAME_MODE -> "player.change_game_mode";
			case SOURCE_SAY -> "source.say";
			case SOURCE_TELL_RAW -> "source.tell_raw";
			case SOURCE_MSG -> "source.msg";
			case SOURCE_ME -> "source.me";
		};
		if (!singleServerOverwrite && !ConfigManager.getBoolean("broadcasts.minecraft_to_minecraft." + eventNode)) {
			return;
		}

		String messageKey = switch (type) {
			case SERVER_STARTED -> "server.start";
			case SERVER_STOPPING -> "server.stop";
			case PLAYER_JOIN -> "player.join";
			case PLAYER_QUIT -> "player.quit";
			case PLAYER_CHAT -> "player.chat";
			case PLAYER_COMMAND -> "player.command";
			case PLAYER_DIE -> "player.die";
			case PLAYER_ADVANCEMENT -> "player.advancement." + placeholders.getOrDefault("type", "task");
			case PLAYER_CHANGE_GAME_MODE -> "player.change_game_mode";
			case SOURCE_SAY -> "source.say";
			case SOURCE_TELL_RAW -> "source.tell_raw";
			case SOURCE_MSG -> "source.msg";
			case SOURCE_ME -> "source.me";
		};

		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return;
		}
		JsonNode messageNode = customMessages.path("minecraft_to_xxxxx");
		for (String part : messageKey.split("\\.")) {
			messageNode = messageNode.path(part);
		}
		String message = messageNode.asText();
		if (message == null || message.isBlank()) {
			return;
		}
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace("{" + entry.getKey() + "}", entry.getValue());
		}

		boolean commandLiteral = type == MinecraftEventPacket.MessageType.PLAYER_COMMAND;
		boolean systemMessage = isSystemMessage(type);

		String templateRoot = singleServerOverwrite ? "single_server_overwrite" : "xxxxx_to_minecraft";

		String effectiveName = placeholders.getOrDefault("display_name", "");
		String roleColor = "white";
		String serverColor = getServerColor(sourceServerName);

		List<TextSegment> segments = DiscordMessageParser.buildMinecraftToMinecraftSegments(
				templateRoot,
				sourceServerName,
				serverColor,
				effectiveName,
				roleColor,
				message,
				systemMessage,
				commandLiteral
		);
		if (segments.isEmpty()) {
			return;
		}

		DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.CHAT, segments);
		if ("single_server".equals(ModeManager.getMode())) {
			NetworkManager.sendPacketToClient(packet, "Internal");
		} else {
			NetworkManager.broadcastToClientsExcept(packet, sourceServerName);
		}
	}

	private boolean isSystemMessage(MinecraftEventPacket.MessageType type) {
		return switch (type) {
			case PLAYER_CHAT, PLAYER_COMMAND, SOURCE_SAY, SOURCE_TELL_RAW, SOURCE_MSG -> false;
			case SOURCE_ME, SERVER_STARTED, SERVER_STOPPING, PLAYER_JOIN, PLAYER_QUIT, PLAYER_DIE, PLAYER_ADVANCEMENT, PLAYER_CHANGE_GAME_MODE -> true;
		};
	}
}
