package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.server.handlers.ServerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encapsulates the Netty server setup and lifecycle.
 *
 * @author Xujiayao
 */
public class NettyServer {

	private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
	private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
	private Channel channel;

	public int start(int port) {
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ServerChannelInitializer());

			channel = b.bind(port).sync().channel();
			int boundPort = ((InetSocketAddress) channel.localAddress()).getPort();
			LOGGER.info("Netty server started and listening on port {}", boundPort);
			return boundPort;

		} catch (InterruptedException e) {
			LOGGER.error("Netty server startup was interrupted.", e);
			Thread.currentThread().interrupt();
		}
		return -1;
	}

	public void stop() {
		LOGGER.info("Stopping Netty server...");
		try {
			if (channel != null) {
				channel.close().syncUninterruptibly();
			}
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
		LOGGER.info("Netty server stopped.");
	}
}
