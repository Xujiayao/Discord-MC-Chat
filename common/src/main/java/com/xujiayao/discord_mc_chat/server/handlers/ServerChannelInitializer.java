package com.xujiayao.discord_mc_chat.server.handlers;

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

/**
 * Initializes the channel pipeline for each new client connection on the server.
 *
 * @author Xujiayao
 */
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {

	@Override
	protected void initChannel(SocketChannel ch) {
		ChannelPipeline pipeline = ch.pipeline();

		// Decoders (Inbound)
		pipeline.addLast(new IdleStateHandler(45, 0, 0));
		pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
		pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
		pipeline.addLast(new JsonPacketDecoder());

		// Encoders (Outbound)
		pipeline.addLast(new LengthFieldPrepender(4));
		pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
		pipeline.addLast(new JsonPacketEncoder());

		// Business Logic Handler
		pipeline.addLast(new ServerBusinessHandler());
	}
}