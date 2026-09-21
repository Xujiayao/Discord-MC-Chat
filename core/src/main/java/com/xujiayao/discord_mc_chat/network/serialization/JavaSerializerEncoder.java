package com.xujiayao.discord_mc_chat.network.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A simple encoder that serializes Java objects into ByteBuf.
 * Replaces the deprecated Netty ObjectEncoder.
 */
public class JavaSerializerEncoder extends MessageToByteEncoder<Serializable> {

	public JavaSerializerEncoder() {
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
		try (ByteBufOutputStream bbos = new ByteBufOutputStream(out);
		     ObjectOutputStream oos = new ObjectOutputStream(bbos)) {
			oos.writeObject(msg);
		}
	}
}
