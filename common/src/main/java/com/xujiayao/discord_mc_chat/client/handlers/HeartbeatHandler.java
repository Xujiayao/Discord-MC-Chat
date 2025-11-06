package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.client.NettyClient;
import com.xujiayao.discord_mc_chat.network.packets.HeartbeatPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles sending heartbeats when the connection is idle and triggers
 * reconnection when the connection is lost.
 *
 * @author Xujiayao
 */
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

	private final NettyClient client;

	public HeartbeatHandler(NettyClient client) {
		this.client = client;
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				// Connection is idle, send a heartbeat
				ctx.writeAndFlush(new HeartbeatPacket());
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		LOGGER.warn("Connection to server lost. Attempting to reconnect...");
		client.scheduleReconnect(ctx.channel(), 2); // Start reconnection with a 2-second delay
		super.channelInactive(ctx);
	}
}
