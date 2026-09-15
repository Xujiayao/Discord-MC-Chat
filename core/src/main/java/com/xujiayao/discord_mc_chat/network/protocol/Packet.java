package com.xujiayao.discord_mc_chat.network.protocol;

/**
 * Base type of every packet exchanged between a DMCC Server and its clients.
 * <p>
 * Packets are plain records serialized as JSON by {@link PacketCodec}. Because DMCC Server and clients are
 * always the same version, the protocol carries no compatibility layer: the {@code type} discriminator in
 * the JSON envelope selects the exact record to deserialize, and unknown fields are ignored so that a
 * field rename shows up as a defaulted value instead of a decode failure.
 *
 * @author Xujiayao
 */
public interface Packet {

	/**
	 * @return The wire type of this packet.
	 */
	PacketType type();
}
