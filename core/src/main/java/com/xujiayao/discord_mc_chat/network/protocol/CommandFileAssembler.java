package com.xujiayao.discord_mc_chat.network.protocol;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles file payloads that arrive as {@link Packets.CommandFileChunk} frames.
 * <p>
 * Chunks are written into a slot indexed by their own index, so the result does not depend on frame
 * ordering, and the buffer is released as soon as the matching {@link Packets.CommandResult} is handled.
 *
 * @author Xujiayao
 */
public final class CommandFileAssembler {

	/**
	 * Upper bound on one assembled file. It guards the server against a client that streams forever.
	 */
	private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;

	private static final Map<String, Slot> SLOTS = new ConcurrentHashMap<>();

	private CommandFileAssembler() {
	}

	/**
	 * Stores one received chunk.
	 *
	 * @param chunk The received chunk.
	 */
	public static void accept(Packets.CommandFileChunk chunk) {
		if (chunk == null || chunk.requestId() == null || chunk.total() <= 0
				|| chunk.index() < 0 || chunk.index() >= chunk.total()) {
			return;
		}
		if ((long) chunk.total() * Packets.CommandFileChunk.CHUNK_BYTES > MAX_FILE_BYTES) {
			return;
		}
		Slot slot = SLOTS.computeIfAbsent(chunk.requestId(), _ -> new Slot(chunk.total(), chunk.fileName()));
		slot.put(chunk);
	}

	/**
	 * Removes and reassembles the payload of a finished request.
	 *
	 * @param requestId Correlation id of the command.
	 * @return The file bytes, or null when nothing (or not everything) arrived.
	 */
	public static byte[] take(String requestId) {
		if (requestId == null) {
			return null;
		}
		Slot slot = SLOTS.remove(requestId);
		return slot == null ? null : slot.assemble();
	}

	/**
	 * Drops any buffered chunks of a request that will never complete.
	 *
	 * @param requestId Correlation id of the command.
	 */
	public static void discard(String requestId) {
		if (requestId != null) {
			SLOTS.remove(requestId);
		}
	}

	/**
	 * Buffered chunks of one request.
	 */
	private static final class Slot {

		private final byte[][] chunks;
		private final String fileName;

		private Slot(int total, String fileName) {
			this.chunks = new byte[total][];
			this.fileName = fileName;
		}

		private void put(Packets.CommandFileChunk chunk) {
			if (chunk.fileName() != null && !chunk.fileName().equals(fileName)) {
				return;
			}
			try {
				chunks[chunk.index()] = java.util.Base64.getDecoder().decode(chunk.data());
			} catch (IllegalArgumentException ignored) {
				// Corrupt chunk: leave the slot empty so the file is reported as unavailable.
			}
		}

		private byte[] assemble() {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for (byte[] chunk : chunks) {
				if (chunk == null) {
					return null;
				}
				out.writeBytes(chunk);
			}
			return Arrays.copyOf(out.toByteArray(), out.size());
		}
	}
}
