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

import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
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
	// Latency samples that are still waiting for their pong, oldest first: every request keeps its own
	// entry (tagged with the timestamp it sent), so a pong answers the request it echoes instead of
	// whichever request happened to register last. Entries are removed by the waiting request itself
	// and by the paths that make a pong impossible (shutdown, lost connection), so none can leak.
	private final Deque<LatencySample> pendingLatencySamples = new ConcurrentLinkedDeque<>();
	private volatile long connectionLatencyMillis;
	private EventLoopGroup workerGroup;
	private volatile Channel channel;
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

		// The pings sent on the lost connection will never be answered.
		releasePendingLatencySamples();

		int delay = reconnectDelay.get();
		workerGroup.schedule(() -> connect(false), delay, TimeUnit.SECONDS);

		// Exponential backoff with cap
		reconnectDelay.set(Math.min(delay * 2, MAX_RECONNECT_DELAY));
	}

	void stop() {
		isRunning.set(false);
		// The connection is going away, so no outstanding ping can be answered anymore.
		releasePendingLatencySamples();
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
				if (!isInNettyEventLoop()) {
					future.awaitUninterruptibly();
				}
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Whether the caller runs on a thread owned by this client's Netty event loop group, i.e. a thread
	 * that must never wait for the group to shut down (it would wait for itself).
	 */
	private boolean isInNettyEventLoop() {
		Channel currentChannel = channel;
		// The channel knows its own event loop, which is exact where the field is set; the thread name
		// stays as a fallback for the window in which channel is still null or already replaced.
		if (currentChannel != null && currentChannel.eventLoop().inEventLoop()) {
			return true;
		}
		return Thread.currentThread().getName().contains("DMCC-NettyClient");
	}

	/**
	 * Releases every outstanding latency sample with the last known latency, used once the connection
	 * can no longer deliver the matching pong.
	 */
	private void releasePendingLatencySamples() {
		for (LatencySample sample = pendingLatencySamples.poll(); sample != null; sample = pendingLatencySamples.poll()) {
			sample.future.complete(connectionLatencyMillis);
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

		long sentAtMillis = System.currentTimeMillis();
		LatencySample sample = new LatencySample(sentAtMillis);
		pendingLatencySamples.add(sample);

		try {
			channel.writeAndFlush(new LatencyPingPacket(sentAtMillis));
			return sample.future.get(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (Exception ignored) {
			return connectionLatencyMillis > 0 ? connectionLatencyMillis : -1;
		} finally {
			// Removal happens for every outcome (answered, timed out, interrupted, released by a
			// disconnect), which is what keeps the pending list from growing with dead entries.
			pendingLatencySamples.remove(sample);
		}
	}

	/**
	 * Records the latency of the connection, as reported by the arriving pong.
	 */
	void updateConnectionLatency(long latencyMillis) {
		connectionLatencyMillis = latencyMillis;
	}

	/**
	 * Answers the in-flight request that sent this timestamp; a pong without a waiting sample is
	 * ignored.
	 */
	void completeLatencySample(long sentAtMillis, long latencyMillis) {
		for (LatencySample sample : pendingLatencySamples) {
			if (sample.sentAtMillis == sentAtMillis) {
				sample.future.complete(latencyMillis);
				return;
			}
		}
	}

	/**
	 * One outstanding latency round trip, identified by the timestamp of the ping it sent.
	 */
	private static final class LatencySample {
		final long sentAtMillis;
		final CompletableFuture<Long> future = new CompletableFuture<>();

		LatencySample(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}
}
