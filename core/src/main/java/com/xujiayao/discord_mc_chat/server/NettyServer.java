package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerDecoder;
import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerEncoder;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

final class NettyServer {

	// Explicit shutdown parameters instead of the Netty defaults (2s quiet period + 15s timeout).
	// Both groups are shut down concurrently, so the worst case here is ~5.5s instead of ~30s.
	private static final long SHUTDOWN_QUIET_PERIOD_MILLIS = 500;
	private static final long SHUTDOWN_TIMEOUT_MILLIS = 5000;

	private final String host;
	private final int port;
	private final String sharedSecret;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;

	NettyServer(String host, int port, String sharedSecret) {
		this.host = host;
		this.port = port;
		this.sharedSecret = sharedSecret;
	}

	String getSharedSecret() {
		return sharedSecret;
	}

	int start() {
		bossGroup = new MultiThreadIoEventLoopGroup(1,
				ExecutorServiceUtils.newThreadFactory("DMCC-NettyServer-Boss"),
				NioIoHandler.newFactory());

		workerGroup = new MultiThreadIoEventLoopGroup(0,
				ExecutorServiceUtils.newThreadFactory("DMCC-NettyServer-Worker"),
				NioIoHandler.newFactory());

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch) {
							ch.pipeline().addLast(
									new IdleStateHandler(30, 0, 0),
									new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
									new LengthFieldPrepender(4),
									new JavaSerializerDecoder(),
									new JavaSerializerEncoder(),
									new ServerHandler(NettyServer.this)
							);
						}
					})
					.option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.SO_KEEPALIVE, true);

			ChannelFuture channelFuture = b.bind(host, port).sync();
			int boundPort = ((InetSocketAddress) channelFuture.channel().localAddress()).getPort();

			LOGGER.info(I18nManager.getDmccTranslation("server.network.listening", boundPort));
			return boundPort;

		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("server.network.bind_failed", port), e);
			// Release the event loop threads that were created above; without this a failed start() leaks
			// them and every later start() attempt would pile up more non-daemon threads.
			shutdownEventLoopGroups();
			return -1;
		}
	}

	void stop() {
		shutdownEventLoopGroups();
	}

	private void shutdownEventLoopGroups() {
		EventLoopGroup worker = workerGroup;
		EventLoopGroup boss = bossGroup;
		// Clearing the fields keeps this method idempotent (e.g. the failed-start path followed by stop()).
		workerGroup = null;
		bossGroup = null;

		Future<?> workerFuture = null;
		Future<?> bossFuture = null;

		if (worker != null) {
			workerFuture = worker.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
		if (boss != null) {
			bossFuture = boss.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}

		// Both graceful shutdowns are already in flight before the first wait, so the total wait is bounded by
		// the slower group (~5.5s) and not by the sum of the two.
		if (workerFuture != null) {
			workerFuture.awaitUninterruptibly();
		}
		if (bossFuture != null) {
			bossFuture.awaitUninterruptibly();
		}
	}
}
