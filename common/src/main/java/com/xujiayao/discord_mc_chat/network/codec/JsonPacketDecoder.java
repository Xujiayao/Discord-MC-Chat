package com.xujiayao.discord_mc_chat.network.codec;

import com.xujiayao.discord_mc_chat.network.packets.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Decodes a JSON string into a Packet object.
 *
 * @author Xujiayao
 */
public class JsonPacketDecoder extends MessageToMessageDecoder<String> {

	@Override
	protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) {
		try {
			Packet packet = JSON_MAPPER.readValue(msg, Packet.class);
			out.add(packet);
		} catch (Exception e) {
			LOGGER.error("Failed to decode JSON packet: " + msg, e);
		}
	}
}
