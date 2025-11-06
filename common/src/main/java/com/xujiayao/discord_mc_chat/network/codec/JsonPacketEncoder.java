package com.xujiayao.discord_mc_chat.network.codec;

import com.xujiayao.discord_mc_chat.network.packets.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Encodes a Packet object into a JSON string.
 *
 * @author Xujiayao
 */
public class JsonPacketEncoder extends MessageToMessageEncoder<Packet> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet msg, List<Object> out) {
		try {
			String json = JSON_MAPPER.writeValueAsString(msg);
			out.add(json);
		} catch (Exception e) {
			LOGGER.error("Failed to encode packet to JSON: " + msg, e);
		}
	}
}
