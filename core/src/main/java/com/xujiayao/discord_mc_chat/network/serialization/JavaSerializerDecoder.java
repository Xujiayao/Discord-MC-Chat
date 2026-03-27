package com.xujiayao.discord_mc_chat.network.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ObjectInputStream;
import java.util.List;

/**
 * A simple decoder that deserializes ByteBuf into Java objects.
 * Replaces the deprecated Netty ObjectDecoder.
 *
 * @author Xujiayao
 */
public class JavaSerializerDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		// Use ObjectInputStream to deserialize the object from the ByteBuf
		try (ByteBufInputStream bbis = new ByteBufInputStream(in);
		     ObjectInputStream ois = new ObjectInputStream(bbis)) {
			out.add(ois.readObject());
		}
	}
}
