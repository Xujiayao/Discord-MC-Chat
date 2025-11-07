package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.client.handlers.ClientChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

	private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicLong reconnectDelay = new AtomicLong(2);

	public NettyClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() {
		running.set(true);
		connect(false);
	}

	public void connect(boolean isReconnect) {
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
				} else if (running.get()) {
					long currentDelay = reconnectDelay.get();
					reconnectDelay.set(Math.min(currentDelay * 2, 256)); // Exponential backoff for next attempt

					if (isReconnect) {
						LOGGER.warn("Reconnect failed! Next attempt in {}s", currentDelay);
					} else {
						LOGGER.warn("Connection failed! First reconnect attempt in {}s", currentDelay);
					}

					f.channel().eventLoop().schedule(() -> this.connect(true), currentDelay, TimeUnit.SECONDS);
				}
			});
		} catch (Exception e) {
			LOGGER.error("Netty client failed to start connection attempt.", e);
		}
	}

	public void resetReconnectDelay() {
		reconnectDelay.set(2);
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

	public boolean isRunning() {
		return running.get();
	}
}
