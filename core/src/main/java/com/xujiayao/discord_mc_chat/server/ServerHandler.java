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
import com.xujiayao.discord_mc_chat.server.minecraft.MinecraftMessageParser;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
							DiscordManager.clientBroadcast(clientName, "server.started", "server.start", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "server.started", p.placeholders);

							// After a Minecraft server starts, perform OP level sync if enabled
							OpSyncManager.syncAll();
							DiscordManager.updateBotPresence();
						}
						case SERVER_STOPPING -> {
							DiscordManager.clientBroadcast(clientName, "server.stopped", "server.stop", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "server.stopped", p.placeholders);
						}
						// Player events
						case PLAYER_JOIN -> {
							DiscordManager.clientBroadcast(clientName, "player.join", "player.join", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.join", p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_QUIT -> {
							DiscordManager.clientBroadcast(clientName, "player.quit", "player.quit", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.quit", p.placeholders);
							DiscordManager.updateBotPresence();
						}
						case PLAYER_CHAT -> {
							DiscordManager.clientBroadcast(clientName, "player.chat", "message", true, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.chat", p.placeholders);
						}
						case PLAYER_COMMAND -> {
							if (isExcludedCommand(p.placeholders.get("command"))) {
								return;
							}
							DiscordManager.clientBroadcast(clientName, "player.command", "message", true, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.command", p.placeholders);
						}
						case PLAYER_DIE -> {
							DiscordManager.clientBroadcast(clientName, "player.die", "player.die", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.die", p.placeholders);
						}
						case PLAYER_ADVANCEMENT -> {
							DiscordManager.clientBroadcast(clientName, "player.advancement", "player.advancement." + p.placeholders.get("type"), false, p.placeholders);
							broadcastToMinecraftClients(clientName, "player.advancement", p.placeholders);
						}
						// Source events
						case SOURCE_SAY -> {
							DiscordManager.clientBroadcast(clientName, "source.say", "message", true, p.placeholders);
							broadcastToMinecraftClients(clientName, "source.say", p.placeholders);
						}
						case SOURCE_TELL_RAW -> {
							String rawCommand = p.placeholders.getOrDefault("raw_command", "");
							if (!isStrictTellrawAtAll(rawCommand)) {
								return;
							}
							DiscordManager.clientBroadcast(clientName, "source.tell_raw", "message", true, p.placeholders);
							broadcastToMinecraftClients(clientName, "source.tell_raw", p.placeholders);
						}
						case SOURCE_MSG -> {
							String rawCommand = p.placeholders.getOrDefault("raw_command", "");
							if (isExcludedCommand(rawCommand)) {
								return;
							}
							DiscordManager.clientBroadcast(clientName, "source.msg", "message", true, p.placeholders);
							broadcastToMinecraftClients(clientName, "source.msg", p.placeholders);
						}
						case SOURCE_ME -> {
							DiscordManager.clientBroadcast(clientName, "source.me", "source.me", false, p.placeholders);
							broadcastToMinecraftClients(clientName, "source.me", p.placeholders);
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
	 * Routes Minecraft-origin events to all online DMCC clients for cross-server broadcast.
	 * The source client will also receive the parsed message so sender sees the final format.
	 *
	 * @param sourceClientName Source DMCC client.
	 * @param eventPath        Event path under broadcasts.between_minecraft_servers.
	 * @param placeholders     Event placeholders.
	 */
	private void broadcastToMinecraftClients(String sourceClientName, String eventPath, Map<String, String> placeholders) {
		if (!Boolean.TRUE.equals(ConfigManager.getBoolean("broadcasts.between_minecraft_servers." + eventPath))) {
			return;
		}

		String serverColor = getClientColor(sourceClientName);
		DiscordEventPacket packet;

		if (isMessageEvent(eventPath)) {
			String displayName = placeholders.getOrDefault("display_name", placeholders.getOrDefault("source_name", "Unknown"));
			String message = buildMessageBodyForMinecraftEvent(eventPath, placeholders);
			String playerUuid = placeholders.getOrDefault("player_uuid", "");
			String roleColor = MinecraftMessageParser.resolveSenderRoleColor(playerUuid);
			List<TextSegment> segments = MinecraftMessageParser.buildCommonMessageSegments(sourceClientName, serverColor, displayName, roleColor, message);
			packet = new DiscordEventPacket(DiscordEventPacket.EventType.CHAT, segments);
		} else {
			String line = buildOthersPlainMessage(sourceClientName, eventPath, placeholders);
			List<TextSegment> segments = MinecraftMessageParser.buildCommonOthersSegments(sourceClientName, serverColor, line);
			packet = new DiscordEventPacket(DiscordEventPacket.EventType.COMMAND, segments);
		}

		NetworkManager.broadcastToClients(packet);
	}

	private boolean isMessageEvent(String eventPath) {
		return "player.chat".equals(eventPath)
				|| "player.command".equals(eventPath)
				|| "source.say".equals(eventPath)
				|| "source.tell_raw".equals(eventPath)
				|| "source.msg".equals(eventPath)
				|| "source.me".equals(eventPath);
	}

	private String buildOthersPlainMessage(String clientName, String eventPath, Map<String, String> placeholders) {
		String path = switch (eventPath) {
			case "server.started" -> "minecraft_to_discord.server.start";
			case "server.stopped" -> "minecraft_to_discord.server.stop";
			case "player.join" -> "minecraft_to_discord.player.join";
			case "player.quit" -> "minecraft_to_discord.player.quit";
			case "player.die" -> "minecraft_to_discord.player.die";
			case "player.advancement" -> "minecraft_to_discord.player.advancement." + placeholders.getOrDefault("type", "task");
			case "source.me" -> "minecraft_to_discord.source.me";
			default -> "";
		};
		if (path.isBlank()) {
			return "";
		}

		JsonNode node = I18nManager.getCustomMessages();
		for (String part : path.split("\\.")) {
			node = node.path(part);
		}
		String message = node.asText();
		message = message.replace("{server}", clientName)
				.replace("{server_color}", getClientColor(clientName));
		for (var entry : placeholders.entrySet()) {
			String value = entry.getValue() == null ? "" : entry.getValue();
			message = message.replace("{" + entry.getKey() + "}", value);
		}
		return sanitizeDiscordMarkdown(message);
	}

	private String sanitizeDiscordMarkdown(String message) {
		if (message == null) {
			return "";
		}
		return message.replaceAll(":[^:]+:", "")
				.replace("**", "")
				.replace("*", "")
				.replace("__", "")
				.replace("~~", "");
	}

	private boolean isExcludedCommand(String rawCommand) {
		if (rawCommand == null || rawCommand.isBlank()) {
			return false;
		}
		JsonNode excludedCommands = ConfigManager.getConfigNode("excluded_commands");
		if (!excludedCommands.isArray()) {
			return false;
		}
		String normalized = rawCommand.trim();
		for (JsonNode node : excludedCommands) {
			String regex = node.asText("");
			if (regex.isBlank()) {
				continue;
			}
			if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(normalized).matches()) {
				return true;
			}
		}
		return false;
	}

	private boolean isStrictTellrawAtAll(String rawCommand) {
		if (rawCommand == null || rawCommand.isBlank()) {
			return false;
		}
		String command = rawCommand.trim().toLowerCase(Locale.ROOT);
		Matcher matcher = Pattern.compile("^/?tellraw\\s+([^\\s]+)\\s+.+$", Pattern.CASE_INSENSITIVE).matcher(command);
		if (!matcher.matches()) {
			return false;
		}
		return "@a".equals(matcher.group(1));
	}

	private String getClientColor(String serverName) {
		JsonNode config = findServerConfig(serverName);
		if (config == null) {
			return "yellow";
		}
		String color = config.path("color").asText("");
		return color == null || color.isBlank() ? "yellow" : color;
	}

	private String buildMessageBodyForMinecraftEvent(String eventPath, Map<String, String> placeholders) {
		if ("player.command".equals(eventPath)) {
			return placeholders.getOrDefault("command", placeholders.getOrDefault("message", ""));
		}
		if ("source.tell_raw".equals(eventPath)) {
			return placeholders.getOrDefault("raw_command", "");
		}
		return placeholders.getOrDefault("message", placeholders.getOrDefault("raw_command", ""));
	}
}
