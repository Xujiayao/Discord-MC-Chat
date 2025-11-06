package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.client.NettyClient;
import com.xujiayao.discord_mc_chat.network.codec.JsonPacketDecoder;
import com.xujiayao.discord_mc_chat.network.codec.JsonPacketEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Initializes the channel pipeline for the client.
 *
 * @author Xujiayao
 */
public class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {

	private final NettyClient client;

	public ClientChannelInitializer(NettyClient client) {
		this.client = client;
	}

	@Override
	protected void initChannel(SocketChannel ch) {
		ChannelPipeline pipeline = ch.pipeline();

		// Decoders
		pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
		pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
		pipeline.addLast(new JsonPacketDecoder());

		// Encoders
		pipeline.addLast(new LengthFieldPrepender(4));
		pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
		pipeline.addLast(new JsonPacketEncoder());

		// Heartbeat and Reconnect
		pipeline.addLast(new IdleStateHandler(0, 40, 0, TimeUnit.SECONDS));
		pipeline.addLast(new HeartbeatHandler(client));

		// Business Logic
		pipeline.addLast(new ClientBusinessHandler());
	}
}
