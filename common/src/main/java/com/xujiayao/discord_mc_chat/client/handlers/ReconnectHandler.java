package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.client.NettyClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles reconnection logic when the client is disconnected.
 *
 * @author Xujiayao
 */
public class ReconnectHandler extends ChannelInboundHandlerAdapter {

	private final NettyClient nettyClient;

	public ReconnectHandler(NettyClient nettyClient) {
		this.nettyClient = nettyClient;
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		LOGGER.warn("Connection to server lost. Attempting to reconnect...");
		nettyClient.doConnect();
		ctx.fireChannelInactive();
	}
}
