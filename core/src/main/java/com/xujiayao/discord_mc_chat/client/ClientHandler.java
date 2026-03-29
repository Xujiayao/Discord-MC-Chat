package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.CommandAutoCompleter;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.network.packets.auth.AuthResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.ChallengePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.DisconnectPacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.HandshakePacket;
import com.xujiayao.discord_mc_chat.network.packets.auth.LoginSuccessPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleAutoCompleteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleAutoCompleteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.console.ConsoleResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteAutoCompleteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteAutoCompleteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.execute.ExecuteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.OpSyncPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.unlink.UnlinkResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordRelayPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftRelayPacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.misc.LatencyPongPacket;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.message.TextSegment;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
final class ClientHandler extends SimpleChannelInboundHandler<Packet> {

	private static final int CONSOLE_COMMAND_TIMEOUT_SECONDS = 10;

	private final NettyClient client;
	private final CompletableFuture<Boolean> initialLoginFuture;
	private boolean allowReconnect = true; // Default to true for network errors

	ClientHandler(NettyClient client, CompletableFuture<Boolean> initialLoginFuture) {
		this.client = client;
		this.initialLoginFuture = initialLoginFuture;
	}

	private static void logDiscordEventForConsole(DiscordRelayPacket p) {
		if (p.replySegments != null && !p.replySegments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.replySegments));
		}
		if (p.segments != null && !p.segments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.segments));
		}
		if (p.type == DiscordRelayPacket.EventType.EDIT && p.editedMessageSegments != null && !p.editedMessageSegments.isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.editedMessageSegments));
		}
	}

	private static void logMinecraftEventForConsole(MinecraftRelayPacket p) {
		if (p.segments != null && !p.segments.isEmpty()) {
			String plain = TextSegment.toPlainText(p.segments);
			if (p.componentPlaceholder != null && !p.componentPlaceholder.isBlank() && plain.contains(p.componentPlaceholder)) {
				String replacement = p.componentText != null ? p.componentText : "";
				LOGGER.info(plain.replace(p.componentPlaceholder, replacement));
			} else {
				LOGGER.info(plain);
			}
		} else if (p.componentJson != null && !p.componentJson.isBlank()) {
			LOGGER.info("[TellRaw] " + p.componentJson);
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.writeAndFlush(new HandshakePacket(client.getServerName(), Constants.VERSION, EnvironmentUtils.getMinecraftVersion()));
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		LOGGER.warn(I18nManager.getDmccTranslation("client.network.disconnected_generic"));

		// Trigger reconnection if this was not an intentional stop AND the server didn't explicitly reject us
		if (client.isRunning()) {
			if (allowReconnect) {
				LOGGER.warn(I18nManager.getDmccTranslation("client.network.reconnecting"));
				client.scheduleReconnect();
			} else {
				// If reconnect is disallowed (e.g. kicked/banned), stop the client.
				client.stop();
			}
		}
		// ELSE: If client.isRunning() is false, it means stop() was called externally (e.g. server shutdown).
		// We do NOT need to call client.stop() again here, as that would be redundant and potentially risky.
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		switch (packet) {
			case ChallengePacket p -> {
				String hash = CryptUtils.sha256(p.salt + client.getSharedSecret());
				ctx.writeAndFlush(new AuthResponsePacket(hash));
			}
			case LoginSuccessPacket p -> {
				I18nManager.load(p.language);
				Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.set(p.overwriteMinecraftSourceMessages);
				LOGGER.info(I18nManager.getDmccTranslation("client.network.connected"));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(true);
				}
			}
			case InfoRequestPacket p -> {
				InfoResponsePacket response = NetworkManager.createInfoResponsePacket();
				response.connectionLatencyMillis = Math.max(0, System.currentTimeMillis() - p.sentAtMillis);
				ctx.writeAndFlush(response);
			}
			case LatencyPongPacket p -> {
				long latency = Math.max(0, System.currentTimeMillis() - p.sentAtMillis);
				client.updateConnectionLatency(latency);
			}
			case ExecuteRequestPacket p -> {
				// Handle DMCC command execution with OP level credential for edge authorization
				StringBuilder responseBuilder = new StringBuilder();
				byte[][] fileDataHolder = new byte[1][];
				String[] fileNameHolder = new String[1];

				CommandSender captureSender = new CommandSender() {
					@Override
					public void reply(String message) {
						if (!responseBuilder.isEmpty()) {
							responseBuilder.append("\n");
						}
						responseBuilder.append(message);
					}

					@Override
					public void replyWithFile(String message, byte[] fileData, String fileName) {
						reply(message);
						fileDataHolder[0] = fileData;
						fileNameHolder[0] = fileName;
					}

					@Override
					public int getOpLevel() {
						return p.opLevel;
					}
				};

				try {
					CommandManager.executeAndWait(captureSender, p.command, p.args)
							.whenComplete((_, ex) -> {
								ExecuteResponsePacket response;
								if (ex != null) {
									response = new ExecuteResponsePacket(p.requestId, I18nManager.getDmccTranslation("commands.execution_failed", ex.getMessage()));
								} else if (fileDataHolder[0] != null) {
									String prefixedFileName = client.getServerName() + "_" + fileNameHolder[0];
									response = new ExecuteResponsePacket(p.requestId, responseBuilder.toString(), fileDataHolder[0], prefixedFileName);
								} else {
									response = new ExecuteResponsePacket(p.requestId, responseBuilder.toString());
								}
								ctx.writeAndFlush(response);
							});
				} catch (Exception e) {
					ctx.writeAndFlush(new ExecuteResponsePacket(p.requestId, I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage())));
				}
			}
			case ConsoleRequestPacket p -> {
				// Handle Minecraft command execution via CoreEvents with callback-based completion
				StringBuilder responseBuilder = new StringBuilder();

				CommandSender captureSender = new CommandSender() {
					@Override
					public void reply(String message) {
						if (!responseBuilder.isEmpty()) {
							responseBuilder.append("\n");
						}
						responseBuilder.append(message);
					}

					@Override
					public int getOpLevel() {
						return p.opLevel;
					}
				};

				CompletableFuture<Void> completionFuture = new CompletableFuture<>();

				EventManager.post(new CoreEvents.MinecraftCommandExecutionEvent(captureSender, p.commandLine, completionFuture));

				// Use the completion future with a timeout to send the response reliably
				completionFuture
						.orTimeout(CONSOLE_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
						.whenComplete((_, _) -> ctx.writeAndFlush(new ConsoleResponsePacket(p.requestId, responseBuilder.toString())));
			}
			case ExecuteAutoCompleteRequestPacket p -> {
				// Handle DMCC command auto-complete with OP level filtering
				List<String> suggestions = CommandAutoCompleter.getSuggestions(p.input, p.opLevel);
				ctx.writeAndFlush(new ExecuteAutoCompleteResponsePacket(client.getServerName(), suggestions));
			}
			case ConsoleAutoCompleteRequestPacket p -> {
				// Handle Minecraft command auto-complete via CoreEvents
				List<String> suggestions = new ArrayList<>();
				EventManager.post(new CoreEvents.MinecraftCommandAutoCompleteEvent(p.input, p.opLevel, suggestions));
				ctx.writeAndFlush(new ConsoleAutoCompleteResponsePacket(client.getServerName(), suggestions));
			}
			case LinkResponsePacket p -> // Handle link code response from server - notify the player
					EventManager.post(new CoreEvents.LinkCodeResponseEvent(p.minecraftUuid, p.code, p.alreadyLinked, p.discordName != null ? p.discordName : ""));
			case UnlinkResponsePacket p -> // Handle unlink response from server - notify the player
					EventManager.post(new CoreEvents.UnlinkResponseEvent(p.minecraftUuid, p.success, p.discordName != null ? p.discordName : ""));
			case OpSyncPacket p -> // Handle OP sync from server - apply OP levels to Minecraft players
					EventManager.post(new CoreEvents.OpSyncEvent(p.opLevels));
			case DiscordRelayPacket p -> {
				// Handle Discord event forwarded from server - render in Minecraft
				if ("multi_server_client".equals(ModeManager.getMode())) {
					logDiscordEventForConsole(p);
				}
				switch (p.type) {
					case CHAT -> EventManager.post(new CoreEvents.DiscordChatMessageEvent(
							p.segments,
							p.replySegments,
							p.mentionNotificationText,
							p.mentionNotificationStyle,
							p.mentionedPlayerUuids,
							p.mentionEveryone
					));
					case COMMAND -> EventManager.post(new CoreEvents.DiscordCommandEvent(p.segments));
					case REACTION -> EventManager.post(new CoreEvents.DiscordReactionEvent(
							p.segments,
							p.replySegments
					));
					case EDIT -> EventManager.post(new CoreEvents.DiscordEditEvent(
							p.segments,
							p.replySegments,
							p.editedMessageSegments
					));
					case DELETE -> EventManager.post(new CoreEvents.DiscordDeleteEvent(
							p.segments,
							p.replySegments
					));
				}
			}
			case MinecraftRelayPacket p -> {
				if ("multi_server_client".equals(ModeManager.getMode())) {
					logMinecraftEventForConsole(p);
				}

				EventManager.post(new CoreEvents.MinecraftRelayMessageEvent(
						p.segments,
						p.componentJson,
						p.componentPlaceholder,
						p.mentionNotificationText,
						p.mentionNotificationStyle,
						p.mentionedPlayerUuids,
						p.mentionEveryone
				));
			}
			case DisconnectPacket p -> {
				// If we receive a DisconnectPacket, it means the server explicitly rejected us.
				// In most cases (whitelist, auth fail, version mismatch), retrying immediately won't help.
				// So we disable reconnection.
				allowReconnect = false;

				String reason = I18nManager.getDmccTranslation(p.key, p.args);
				LOGGER.error(I18nManager.getDmccTranslation("client.network.disconnected_reason", reason));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(false);
				}
				ctx.close();
			}
			case null, default ->
					LOGGER.warn(I18nManager.getDmccTranslation("client.network.unexpected_packet", packet == null ? "null" : packet.getClass().getSimpleName()));
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				ctx.writeAndFlush(new KeepAlivePacket());
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error(I18nManager.getDmccTranslation("client.network.connect_failed"), cause);
		if (!initialLoginFuture.isDone()) {
			initialLoginFuture.complete(false);
		}
		ctx.close();
	}
}
