package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.packets.MiscPackets.LatencyPingPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerDecoder;
import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerEncoder;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encapsulates the Netty client, connection, and reconnection logic.
 *
 * @author Xujiayao
 */
final class NettyClient {

	private static final int MAX_RECONNECT_DELAY = 512;
	private final String host;
	private final int port;
	private final String serverName;
	private final String sharedSecret;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final AtomicInteger reconnectDelay = new AtomicInteger(2); // Initial delay seconds
	private final AtomicReference<CompletableFuture<Long>> latencyFuture = new AtomicReference<>();
	private volatile long connectionLatencyMillis;
	private EventLoopGroup workerGroup;
	private Channel channel;
	private CompletableFuture<Boolean> initialLoginFuture;

	NettyClient(String host, int port, String serverName, String sharedSecret) {
		this.host = host;
		this.port = port;
		this.serverName = serverName;
		this.sharedSecret = sharedSecret;
	}

	String getServerName() {
		return serverName;
	}

	String getSharedSecret() {
		return sharedSecret;
	}

	boolean start() {
		isRunning.set(true);
		initialLoginFuture = new CompletableFuture<>();

		// Use MultiThreadIoEventLoopGroup with NioIoHandler
		workerGroup = new MultiThreadIoEventLoopGroup(0,
				ExecutorServiceUtils.newThreadFactory("DMCC-NettyClient"),
				NioIoHandler.newFactory());

		connect(true);

		try {
			// Wait for the INITIAL handshake to complete
			return initialLoginFuture.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("client.network.connect_failed"), e);
			stop();
			return false;
		}
	}

	private void connect(boolean isInitialAttempt) {
		if (!isRunning.get()) return;

		long connectStartNanos = System.nanoTime();

		Bootstrap b = new Bootstrap();
		b.group(workerGroup);
		b.channel(NioSocketChannel.class);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) {
				ch.pipeline().addLast(
						new IdleStateHandler(30, 15, 0),
						new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
						new LengthFieldPrepender(4),
						new JavaSerializerDecoder(),
						new JavaSerializerEncoder(),
						new ClientHandler(NettyClient.this, initialLoginFuture)
				);
			}
		});

		if (isInitialAttempt) {
			LOGGER.info(I18nManager.getDmccTranslation("client.network.connecting", host, port));
		}

		b.connect(host, port).addListener((ChannelFuture future) -> {
			if (future.isSuccess()) {
				// Connection established
				this.channel = future.channel();
				reconnectDelay.set(1); // Reset delay on success
				connectionLatencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStartNanos);
			} else {
				if (isInitialAttempt) {
					initialLoginFuture.completeExceptionally(future.cause());
				} else {
					LOGGER.warn(I18nManager.getDmccTranslation("client.network.reconnect_failed", reconnectDelay.get()));
					scheduleReconnect();
				}
			}
		});
	}

	void sendPacket(Packet packet) {
		if (channel != null && channel.isActive()) {
			channel.writeAndFlush(packet);
		} else {
			LOGGER.warn(I18nManager.getDmccTranslation("client.network.send_while_disconnected", packet.getClass().getSimpleName()));
		}
	}

	void scheduleReconnect() {
		if (!isRunning.get()) return;

		int delay = reconnectDelay.get();
		workerGroup.schedule(() -> connect(false), delay, TimeUnit.SECONDS);

		// Exponential backoff with cap
		reconnectDelay.set(Math.min(delay * 2, MAX_RECONNECT_DELAY));
	}

	void stop() {
		isRunning.set(false);
		if (channel != null) {
			channel.close();
		}
		if (workerGroup != null) {
			// Initiates shutdown gracefully
			Future<?> future = workerGroup.shutdownGracefully();

			// CRITICAL FIX: Prevent Deadlock
			// We must ONLY wait for shutdown if we are NOT running inside the Netty thread.
			// If we are inside the Netty thread (e.g. called from ClientHandler), waiting for ourselves to die causes a deadlock.
			// But if we are on the Main thread (ServerStopped event), we MUST wait to prevent ClassLoader issues.
			try {
				if (!Thread.currentThread().getName().contains("DMCC-NettyClient")) {
					future.awaitUninterruptibly();
				}
			} catch (Exception ignored) {
			}
		}
	}

	boolean isRunning() {
		return isRunning.get();
	}

	boolean isConnected() {
		return channel != null && channel.isActive();
	}

	long getConnectionLatencyMillis() {
		return connectionLatencyMillis;
	}

	long requestLatencySample(long timeoutMillis) {
		if (channel == null || !channel.isActive()) {
			return -1;
		}

		CompletableFuture<Long> future = new CompletableFuture<>();
		CompletableFuture<Long> previous = latencyFuture.getAndSet(future);
		if (previous != null && !previous.isDone()) {
			previous.complete(connectionLatencyMillis);
		}

		long sentAtMillis = System.currentTimeMillis();
		channel.writeAndFlush(new LatencyPingPacket(sentAtMillis));

		try {
			return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (Exception ignored) {
			latencyFuture.compareAndSet(future, null);
			return connectionLatencyMillis > 0 ? connectionLatencyMillis : -1;
		}
	}

	void updateConnectionLatency(long latencyMillis) {
		connectionLatencyMillis = latencyMillis;

		CompletableFuture<Long> future = latencyFuture.getAndSet(null);
		if (future != null && !future.isDone()) {
			future.complete(latencyMillis);
		}
	}
}
