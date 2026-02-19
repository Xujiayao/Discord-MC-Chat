package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.CommandAutoCompleter;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.AuthResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.ChallengePacket;
import com.xujiayao.discord_mc_chat.network.packets.CommandAutoCompleteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.CommandAutoCompleteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.DisconnectPacket;
import com.xujiayao.discord_mc_chat.network.packets.ExecuteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.ExecuteResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.HandshakePacket;
import com.xujiayao.discord_mc_chat.network.packets.InfoRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.LatencyPongPacket;
import com.xujiayao.discord_mc_chat.network.packets.LoginSuccessPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
public class ClientHandler extends SimpleChannelInboundHandler<Packet> {

	private final NettyClient client;
	private final CompletableFuture<Boolean> initialLoginFuture;
	private boolean allowReconnect = true; // Default to true for network errors

	public ClientHandler(NettyClient client, CompletableFuture<Boolean> initialLoginFuture) {
		this.client = client;
		this.initialLoginFuture = initialLoginFuture;
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
				};

				try {
					CommandManager.executeAndWait(captureSender, p.command, p.args)
							.whenComplete((v, ex) -> {
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
			case CommandAutoCompleteRequestPacket p -> {
				List<String> suggestions = CommandAutoCompleter.getSuggestions(p.input);
				ctx.writeAndFlush(new CommandAutoCompleteResponsePacket(client.getServerName(), suggestions));
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
