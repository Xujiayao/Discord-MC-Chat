package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.network.packets.HeartbeatPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Sends a heartbeat packet when the connection is idle.
 *
 * @author Xujiayao
 */
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				ctx.writeAndFlush(new HeartbeatPacket());
			}
		}
	}
}
