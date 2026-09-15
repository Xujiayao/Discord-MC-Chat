package com.xujiayao.discord_mc_chat.network.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Reads one JSON frame and turns it into a {@link Packet}.
 * <p>
 * A malformed frame raises {@link ProtocolException}, which the channel's exception handler turns into a
 * clean disconnect instead of crashing the event loop.
 *
 * @author Xujiayao
 */
public class JsonPacketDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		byte[] data = new byte[in.readableBytes()];
		in.readBytes(data);
		out.add(PacketCodec.decode(data));
	}
}
