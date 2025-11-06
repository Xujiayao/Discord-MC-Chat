package com.xujiayao.discord_mc_chat.server.handlers;

import com.xujiayao.discord_mc_chat.network.packets.HeartbeatPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.network.packets.PlayerChatPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles server-side business logic.
 *
 * @author Xujiayao
 */
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Packet> {

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		LOGGER.info("Client connected: " + ctx.channel().remoteAddress());
		// Here you can add the channel to a managed list
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof PlayerChatPacket(String serverName, String playerName, String message)) {
			LOGGER.info(String.format("[%s] <%s> %s", serverName, playerName, message));
			// TODO: Format and send to Discord
		} else if (packet instanceof HeartbeatPacket) {
			// Heartbeat received, connection is alive. No action needed.
		}
		// TODO: Handle other packet types
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		LOGGER.info("Client disconnected: " + ctx.channel().remoteAddress());
		// Here you can remove the channel from the managed list
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.READER_IDLE) {
				LOGGER.warn("Did not receive heartbeat from client " + ctx.channel().remoteAddress() + ". Closing connection.");
				ctx.close();
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error("Exception caught in server handler", cause);
		ctx.close();
	}
}
