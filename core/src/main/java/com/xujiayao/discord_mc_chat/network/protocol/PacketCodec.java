package com.xujiayao.discord_mc_chat.network.protocol;

import com.xujiayao.discord_mc_chat.Constants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Encodes and decodes {@link Packet} instances as JSON.
 * <p>
 * The wire format is a two-field envelope: a {@code type} discriminator plus the record payload. Decoding
 * goes through an explicit {@link PacketType} to record mapping, so a peer can never talk the receiver
 * into instantiating an arbitrary class, and there is no Java object deserialization anywhere in the
 * protocol.
 *
 * @author Xujiayao
 */
public final class PacketCodec {

	private static final ObjectMapper MAPPER = Constants.JSON_MAPPER;

	private static final Map<PacketType, Class<? extends Packet>> CLASSES = Map.ofEntries(
			Map.entry(PacketType.HANDSHAKE, Packets.Handshake.class),
			Map.entry(PacketType.CHALLENGE, Packets.Challenge.class),
			Map.entry(PacketType.AUTH_RESPONSE, Packets.AuthResponse.class),
			Map.entry(PacketType.LOGIN_SUCCESS, Packets.LoginSuccess.class),
			Map.entry(PacketType.DISCONNECT, Packets.Disconnect.class),
			Map.entry(PacketType.KEEP_ALIVE, Packets.KeepAlive.class),
			Map.entry(PacketType.LATENCY_PING, Packets.LatencyPing.class),
			Map.entry(PacketType.LATENCY_PONG, Packets.LatencyPong.class),
			Map.entry(PacketType.COMMAND_REQUEST, Packets.CommandRequest.class),
			Map.entry(PacketType.COMMAND_RESULT, Packets.CommandResult.class),
			Map.entry(PacketType.COMMAND_FILE_CHUNK, Packets.CommandFileChunk.class),
			Map.entry(PacketType.AUTOCOMPLETE_REQUEST, Packets.AutoCompleteRequest.class),
			Map.entry(PacketType.AUTOCOMPLETE_RESULT, Packets.AutoCompleteResult.class),
			Map.entry(PacketType.INFO_REQUEST, Packets.InfoRequest.class),
			Map.entry(PacketType.INFO_SNAPSHOT, Packets.InfoSnapshot.class),
			Map.entry(PacketType.LINK_REQUEST, Packets.LinkRequest.class),
			Map.entry(PacketType.LINK_RESULT, Packets.LinkResult.class),
			Map.entry(PacketType.UNLINK_REQUEST, Packets.UnlinkRequest.class),
			Map.entry(PacketType.UNLINK_RESULT, Packets.UnlinkResult.class),
			Map.entry(PacketType.OP_SYNC, Packets.OpSync.class),
			Map.entry(PacketType.DISCORD_RELAY, Packets.DiscordRelay.class),
			Map.entry(PacketType.MINECRAFT_RELAY, Packets.MinecraftRelay.class),
			Map.entry(PacketType.MINECRAFT_EVENT, Packets.MinecraftEvent.class),
			Map.entry(PacketType.CONSOLE_LOG_BATCH, Packets.ConsoleLogBatch.class)
	);

	private PacketCodec() {
	}

	/**
	 * Serializes a packet.
	 *
	 * @param packet Packet to serialize.
	 * @return The JSON bytes to put into one Netty frame.
	 */
	public static byte[] encode(Packet packet) {
		return MAPPER.writeValueAsBytes(new Envelope(packet.type().name(), packet));
	}

	/**
	 * Deserializes one frame.
	 *
	 * @param data JSON bytes of exactly one frame.
	 * @return The decoded packet.
	 * @throws ProtocolException When the frame is not a packet DMCC understands.
	 */
	public static Packet decode(byte[] data) {
		JsonNode root;
		try {
			root = MAPPER.readTree(data);
		} catch (RuntimeException e) {
			throw new ProtocolException("Packet frame is not valid JSON", e);
		}
		if (root == null || !root.isObject()) {
			throw new ProtocolException("Packet frame is not a JSON object");
		}

		String typeName = root.path("type").asString("");
		PacketType type;
		try {
			type = PacketType.valueOf(typeName);
		} catch (IllegalArgumentException e) {
			throw new ProtocolException("Unknown packet type: " + typeName);
		}

		JsonNode payload = root.path("payload");
		if (payload.isMissingNode() || payload.isNull()) {
			throw new ProtocolException("Packet " + typeName + " has no payload");
		}

		Packet packet;
		try {
			packet = MAPPER.treeToValue(payload, CLASSES.get(type));
		} catch (RuntimeException e) {
			throw new ProtocolException("Packet " + typeName + " payload could not be read", e);
		}
		if (packet == null) {
			throw new ProtocolException("Packet " + typeName + " decoded to null");
		}
		return packet;
	}

	/**
	 * The JSON envelope wrapping every packet.
	 *
	 * @param type    Wire type name.
	 * @param payload The packet record itself.
	 */
	private record Envelope(String type, Object payload) {
	}
}
