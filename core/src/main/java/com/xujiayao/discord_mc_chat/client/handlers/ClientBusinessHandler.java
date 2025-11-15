package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.commands.CommandEvents;
import com.xujiayao.discord_mc_chat.network.Packet;
import com.xujiayao.discord_mc_chat.network.Packets;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
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
		if (packet instanceof Packets.HandshakeResponse(boolean success, String message)) {
			if (success) {
				LOGGER.info("Handshake with server successful with message: \"{}\"", message);
			} else {
				LOGGER.error("Handshake with server failed with message: \"{}\"", message);
				LOGGER.error("This is a fatal error. DMCC client will now shut down.");

				// Post a StopEvent to trigger a graceful, application-wide shutdown.
				// This reuses the existing shutdown logic in CommandEventHandler.
				EventManager.post(new CommandEvents.StopEvent());
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
