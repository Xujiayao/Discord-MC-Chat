package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.client.NettyClient;
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

	private final NettyClient client;

	public ClientBusinessHandler(NettyClient client) {
		this.client = client;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof Packets.HandshakeResponse(boolean success, String message)) {
			if (success) {
				LOGGER.info("Handshake with server successful with message: \"{}\"", message);
			} else {
				LOGGER.error("Handshake with server failed with message: \"{}\"", message);
				LOGGER.error("This is a fatal error. DMCC client will shut down.");
				client.stop(); // Stop the client and prevent reconnection
			}
		}
		// TODO: Handle other packet types
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error("Exception caught in client handler", cause);
		ctx.close();
	}
}
