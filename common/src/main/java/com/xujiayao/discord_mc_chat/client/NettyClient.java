package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.client.handlers.ClientChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encapsulates the Netty client setup, connection and reconnection logic.
 *
 * @author Xujiayao
 */
public class NettyClient {

	private final String host;
	private final int port;
	private final EventLoopGroup group = new NioEventLoopGroup();

	private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;
	private static final int MAX_RECONNECT_DELAY_SECONDS = 256;
	private int currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;

	private Bootstrap bootstrap;
	private volatile boolean running = false;

	public NettyClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() {
		running = true;
		bootstrap = new Bootstrap();
		bootstrap.group(group)
				.channel(NioSocketChannel.class)
				.handler(new ClientChannelInitializer(this));

		LOGGER.info("Netty client started. Attempting to connect to " + host + ":" + port);
		doConnect();
	}

	public void doConnect() {
		if (!running) {
			return;
		}

		ChannelFuture future = bootstrap.connect(host, port);
		future.addListener((ChannelFutureListener) futureListener -> {
			if (futureListener.isSuccess()) {
				LOGGER.info("Successfully connected to the server.");
				// On successful connection, reset the reconnect delay
				currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
			} else {
				LOGGER.warn("Failed to connect to the server. Retrying in {} seconds...", currentReconnectDelay);
				futureListener.channel().eventLoop().schedule(this::doConnect, currentReconnectDelay, TimeUnit.SECONDS);

				// Apply exponential backoff for the next attempt
				currentReconnectDelay = Math.min(currentReconnectDelay * 2, MAX_RECONNECT_DELAY_SECONDS);
			}
		});
	}

	public void stop() {
		running = false;
		group.shutdownGracefully();
		LOGGER.info("Netty client stopped.");
	}
}
