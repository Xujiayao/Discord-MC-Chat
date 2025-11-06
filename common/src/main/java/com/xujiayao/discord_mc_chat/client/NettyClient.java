package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.client.handlers.ClientChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encapsulates the Netty client, connection, and reconnection logic.
 *
 * @author Xujiayao
 */
public class NettyClient {

	private final String host;
	private final int port;
	private Channel channel;

	private final EventLoopGroup group = new NioEventLoopGroup();
	private final AtomicBoolean running = new AtomicBoolean(false);

	public NettyClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() {
		running.set(true);
		connect();
	}

	public void connect() {
		if (!running.get()) {
			return; // Do not attempt to connect if the client is stopped
		}

		try {
			Bootstrap b = new Bootstrap();
			b.group(group)
					.channel(NioSocketChannel.class)
					.handler(new ClientChannelInitializer(this));

			LOGGER.info("Attempting to connect to server at " + host + ":" + port);
			ChannelFuture future = b.connect(host, port);

			future.addListener((ChannelFutureListener) f -> {
				if (f.isSuccess()) {
					channel = f.channel();
					LOGGER.info("Successfully connected to server.");
				} else {
					LOGGER.warn("Failed to connect to server. Retrying...");
					scheduleReconnect(f.channel(), 2); // Start with a 2-second delay
				}
			});
		} catch (Exception e) {
			LOGGER.error("Netty client failed to start connection attempt.", e);
		}
	}

	public void scheduleReconnect(Channel ch, long delay) {
		if (!running.get()) {
			return; // Do not schedule reconnect if the client is stopping
		}

		final long nextDelay = Math.min(delay * 2, 256); // Exponential backoff, capped at 256 seconds

		ch.eventLoop().schedule(() -> {
			LOGGER.info("Reconnecting... (next attempt in " + nextDelay + "s)");
			connect();
		}, delay, TimeUnit.SECONDS);
	}

	public void stop() {
		if (!running.compareAndSet(true, false)) {
			// Already stopped or stopping
			return;
		}

		LOGGER.info("Stopping Netty client...");
		try {
			if (channel != null && channel.isOpen()) {
				channel.close().syncUninterruptibly();
			}
			group.shutdownGracefully().syncUninterruptibly();
		} finally {
			LOGGER.info("Netty client stopped.");
		}
	}
}
