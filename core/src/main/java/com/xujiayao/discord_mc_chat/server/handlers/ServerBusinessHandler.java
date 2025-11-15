package com.xujiayao.discord_mc_chat.server.handlers;

import com.xujiayao.discord_mc_chat.network.Packet;
import com.xujiayao.discord_mc_chat.network.Packets;
import com.xujiayao.discord_mc_chat.server.ChannelManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side business logic.
 *
 * @author Xujiayao
 */
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Packet> {

	private static final AttributeKey<Boolean> IS_LOGGED_IN = AttributeKey.valueOf("isLoggedIn");

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.channel().attr(IS_LOGGED_IN).set(false);
		LOGGER.info("Client connected from {}, waiting for handshake...", ctx.channel().remoteAddress());
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		Boolean isLoggedIn = ctx.channel().attr(IS_LOGGED_IN).get();

		if (!isLoggedIn) {
			if (packet instanceof Packets.Handshake(String serverName)) {
				if (ChannelManager.registerChannel(serverName, ctx.channel())) {
					ctx.channel().attr(IS_LOGGED_IN).set(true);
					ctx.writeAndFlush(new Packets.HandshakeResponse(true, "Handshake successful."));
				} else {
					// Duplicate server name, send rejection but do not close. The client will close itself.
					String reason = "Duplicate server name: " + serverName;
					ctx.writeAndFlush(new Packets.HandshakeResponse(false, reason));
				}
			} else {
				// First packet must be a Handshake packet
				LOGGER.warn("First packet from {} was not a Handshake packet. Closing connection.", ctx.channel().remoteAddress());
				ctx.close();
			}
			return;
		}

		// From here, the client is logged in.
		if (packet instanceof Packets.Heartbeat) {
			// Heartbeat received, connection is alive. No action needed.
		}
		// TODO: Handle other packet types
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		ChannelManager.unregisterChannel(ctx.channel());
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
