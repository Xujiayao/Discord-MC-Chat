package com.xujiayao.discord_mc_chat.network.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Writes a {@link Packet} into the channel as one JSON frame.
 *
 * @author Xujiayao
 */
public class JsonPacketEncoder extends MessageToByteEncoder<Packet> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
		out.writeBytes(PacketCodec.encode(msg));
	}
}
