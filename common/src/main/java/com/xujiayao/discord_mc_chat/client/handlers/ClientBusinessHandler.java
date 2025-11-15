package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.network.Packet;
import com.xujiayao.discord_mc_chat.network.Packets;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side business logic.
 *
 * @author Xujiayao
 */
public class ClientBusinessHandler extends SimpleChannelInboundHandler<Packet> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof Packets.DisplayMessage(String jsonMessage)) {
			LOGGER.info("Received message from server: {}", jsonMessage);
			// TODO: Use Minecraft API to display the message in-game
		}
		// TODO: Handle other packet types
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error("Exception caught in client handler", cause);
		ctx.close();
	}
}
