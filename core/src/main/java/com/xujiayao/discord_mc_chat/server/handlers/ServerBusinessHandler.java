package com.xujiayao.discord_mc_chat.server.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.network.Packet;
import com.xujiayao.discord_mc_chat.network.Packets;
import com.xujiayao.discord_mc_chat.server.ChannelManager;
import com.xujiayao.discord_mc_chat.utils.CryptoUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.VERSION;

/**
 * Handles server-side business logic.
 *
 * @author Xujiayao
 */
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Packet> {

	private enum HandshakeState {
		WAITING_FOR_HELLO,
		WAITING_FOR_RESPONSE,
		COMPLETED
	}

	private static final AttributeKey<HandshakeState> HANDSHAKE_STATE = AttributeKey.valueOf("handshakeState");
	private static final AttributeKey<String> SERVER_NAME = AttributeKey.valueOf("serverName");

	private static final Map<Channel, String> CHALLENGE_MAP = new ConcurrentHashMap<>();

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.channel().attr(HANDSHAKE_STATE).set(HandshakeState.WAITING_FOR_HELLO);
		LOGGER.info("Client connected from {}, waiting for handshake...", ctx.channel().remoteAddress());
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		HandshakeState state = ctx.channel().attr(HANDSHAKE_STATE).get();

		if (state != HandshakeState.COMPLETED) {
			handleHandshake(ctx, packet, state);
			return;
		}

		// From here, the client is logged in and handshake is completed.
		if (packet instanceof Packets.Heartbeat) {
			// Heartbeat received, connection is alive. No action needed.
		}
		// TODO: Handle other packet types
	}

	private void handleHandshake(ChannelHandlerContext ctx, Packet packet, HandshakeState state) {
		switch (state) {
			case WAITING_FOR_HELLO -> {
				if (packet instanceof Packets.ClientHello(String serverName, String version)) {
					// Whitelist check
					if (!isWhitelisted(serverName)) {
						LOGGER.warn("Client \"{}\" from {} is not in the whitelist. Rejecting.", serverName, ctx.channel().remoteAddress());
						ctx.writeAndFlush(new Packets.HandshakeFailure("handshake.error.not_whitelisted")).addListener(future -> ctx.close());
						return;
					}

					// Version check
					if (!Objects.equals(version, VERSION)) {
						LOGGER.warn("Client \"{}\" has an incompatible version (Client: {}, Server: {}). Rejecting.", serverName, version, VERSION);
						ctx.writeAndFlush(new Packets.HandshakeFailure("handshake.error.invalid_version")).addListener(future -> ctx.close());
						return;
					}

					// Duplicate server name check
					if (ChannelManager.isNameRegistered(serverName)) {
						LOGGER.warn("Client from {} tried to connect with a duplicate server name: {}", ctx.channel().remoteAddress(), serverName);
						ctx.writeAndFlush(new Packets.HandshakeFailure("handshake.error.duplicate_name")).addListener(future -> ctx.close());
						return;
					}

					// Generate and send challenge
					String challenge = CryptoUtils.generateRandomString(32);
					CHALLENGE_MAP.put(ctx.channel(), challenge);
					ctx.channel().attr(SERVER_NAME).set(serverName);
					ctx.channel().attr(HANDSHAKE_STATE).set(HandshakeState.WAITING_FOR_RESPONSE);
					ctx.writeAndFlush(new Packets.ServerChallenge(challenge));
				} else {
					LOGGER.warn("First packet from {} was not a ClientHello packet. Closing connection.", ctx.channel().remoteAddress());
					ctx.close();
				}
			}
			case WAITING_FOR_RESPONSE -> {
				if (packet instanceof Packets.ClientResponse(String responseHash)) {
					String serverName = ctx.channel().attr(SERVER_NAME).get();
					String challenge = CHALLENGE_MAP.remove(ctx.channel());

					if (challenge == null) {
						LOGGER.warn("No challenge found for client {}. Closing connection.", ctx.channel().remoteAddress());
						ctx.close();
						return;
					}

					String sharedSecret = ConfigManager.getString("multi_server.security.shared_secret");
					String expectedHash = CryptoUtils.hmacSha256(sharedSecret, challenge);

					if (Objects.equals(responseHash, expectedHash)) {
						// Authentication successful
						ctx.channel().attr(HANDSHAKE_STATE).set(HandshakeState.COMPLETED);
						ChannelManager.registerChannel(serverName, ctx.channel());
						String language = ConfigManager.getString("language");
						ctx.writeAndFlush(new Packets.HandshakeSuccess("handshake.success", language));
						LOGGER.info("Client \"{}\" from {} authenticated successfully.", serverName, ctx.channel().remoteAddress());
					} else {
						// Authentication failed
						LOGGER.warn("Authentication failed for client \"{}\" from {}. Closing connection.", serverName, ctx.channel().remoteAddress());
						ctx.writeAndFlush(new Packets.HandshakeFailure("handshake.error.authentication_failed")).addListener(future -> ctx.close());
					}
				} else {
					LOGGER.warn("Expected ClientResponse packet from {}, but got {}. Closing connection.", ctx.channel().remoteAddress(), packet.getClass().getSimpleName());
					ctx.close();
				}
			}
		}
	}

	private boolean isWhitelisted(String serverName) {
		if ("local".equals(serverName)) {
			// single_server mode's internal client is always allowed
			return true;
		}

		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode.isArray()) {
			for (JsonNode serverNode : serversNode) {
				if (serverName.equals(serverNode.path("name").asText())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		ChannelManager.unregisterChannel(ctx.channel());
		CHALLENGE_MAP.remove(ctx.channel());
		LOGGER.info("Client disconnected: {}", ctx.channel().remoteAddress());
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.READER_IDLE) {
				LOGGER.warn("Did not receive heartbeat from client {}. Closing connection.", ctx.channel().remoteAddress());
				ctx.close();
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error("Exception caught in server handler for {}", ctx.channel().remoteAddress(), cause);
		ctx.close();
	}
}
