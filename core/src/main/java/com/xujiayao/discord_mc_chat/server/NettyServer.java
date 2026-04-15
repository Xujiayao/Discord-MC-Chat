package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerDecoder;
import com.xujiayao.discord_mc_chat.network.serialization.JavaSerializerEncoder;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
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

import java.net.InetSocketAddress;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encapsulates the Netty server setup and lifecycle.
 *
 * @author Xujiayao
 */
final class NettyServer {

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
			return -1;
		}
	}

	void stop() {
		io.netty.util.concurrent.Future<?> workerFuture = null;
		io.netty.util.concurrent.Future<?> bossFuture = null;

		if (workerGroup != null) {
			workerFuture = workerGroup.shutdownGracefully();
		}
		if (bossGroup != null) {
			bossFuture = bossGroup.shutdownGracefully();
		}

		if (workerFuture != null) {
			workerFuture.awaitUninterruptibly();
		}
		if (bossFuture != null) {
			bossFuture.awaitUninterruptibly();
		}
	}
}
