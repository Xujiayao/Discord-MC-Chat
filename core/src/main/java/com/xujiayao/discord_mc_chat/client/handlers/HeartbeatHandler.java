package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.client.NettyClient;
import com.xujiayao.discord_mc_chat.network.Packets;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.VERSION;

/**
 * Handles sending heartbeats when the connection is idle and triggers
 * reconnection when the connection is lost. Also handles the initial handshake.
 *
 * @author Xujiayao
 */
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

	private final NettyClient client;

	public HeartbeatHandler(NettyClient client) {
		this.client = client;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Connection is active, send ClientHello packet
		String serverName = "single_server".equals(ModeManager.getMode())
				? "local"
				: ConfigManager.getString("multi_server.server_name");

		LOGGER.info("Connected to server. Sending handshake with server name: \"{}\"", serverName);
		ctx.writeAndFlush(new Packets.ClientHello(serverName, VERSION));

		// Reset reconnect delay on successful connection
		client.resetReconnectDelay();
		super.channelActive(ctx);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				// Connection is idle, send a heartbeat
				ctx.writeAndFlush(new Packets.Heartbeat());
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (client.isRunning()) {
			LOGGER.warn("Connection to server lost. Reconnecting...");
			client.connect(true); // Attempt to reconnect immediately
		}

		super.channelInactive(ctx);
	}
}
