package com.xujiayao.discord_mc_chat.network.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.List;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * A simple decoder that deserializes ByteBuf into Java objects.
 * Replaces the deprecated Netty ObjectDecoder.
 */
public class JavaSerializerDecoder extends ByteToMessageDecoder {

	// A single frame is already capped at 1 MiB by LengthFieldBasedFrameDecoder, so these limits can never
	// reject a packet that fits on the wire; they only stop a small frame from expanding into a huge object
	// graph while it is being read.
	private static final int MAX_ARRAY_LENGTH = 1048576;
	private static final int MAX_DEPTH = 32;
	private static final long MAX_REFERENCES = 1048576;
	private static final long MAX_STREAM_BYTES = 1048576;

	/**
	 * Whitelist of everything a DMCC packet may contain: the protocol classes themselves, boxed primitives,
	 * strings and enums ({@code java.lang}) and collections ({@code java.util}, which also covers
	 * {@code java.util.concurrent}). Everything else is rejected. {@code java.lang.reflect} and
	 * {@code java.lang.invoke} are excluded explicitly because they are the entry points of well-known
	 * deserialization gadget chains and no packet carries such a value; the {@code java.lang} prefix alone
	 * would cover them.
	 */
	private static final ObjectInputFilter SERIAL_FILTER = filterInfo -> {
		if (filterInfo.depth() > MAX_DEPTH
				|| filterInfo.references() > MAX_REFERENCES
				|| filterInfo.streamBytes() > MAX_STREAM_BYTES
				|| filterInfo.arrayLength() > MAX_ARRAY_LENGTH) {
			return ObjectInputFilter.Status.REJECTED;
		}

		// A null class means the filter is called for a limit check only, which was handled above
		Class<?> serialClass = filterInfo.serialClass();
		if (serialClass == null) {
			return ObjectInputFilter.Status.UNDECIDED;
		}

		return isAllowedClass(serialClass)
				? ObjectInputFilter.Status.ALLOWED
				: ObjectInputFilter.Status.REJECTED;
	};

	public JavaSerializerDecoder() {
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		try (ByteBufInputStream bbis = new ByteBufInputStream(in);
		     ObjectInputStream ois = new ObjectInputStream(bbis)) {
			ois.setObjectInputFilter(SERIAL_FILTER);
			out.add(ois.readObject());
		} catch (Exception e) {
			// A rejected or unreadable packet must never reach the pipeline: drop the rest of the frame and
			// close the connection instead of letting the sender keep feeding a corrupted stream
			LOGGER.warn(I18nManager.getDmccTranslation("utils.network.packet_rejected", ctx.channel().remoteAddress(), e.toString()));
			in.skipBytes(in.readableBytes());
			ctx.close();
		}
	}

	private static boolean isAllowedClass(Class<?> serialClass) {
		Class<?> type = serialClass;
		while (type.isArray()) {
			type = type.getComponentType();
		}
		if (type.isPrimitive()) {
			return true;
		}

		String name = type.getName();
		return name.startsWith("com.xujiayao.discord_mc_chat.")
				|| name.startsWith("java.util.")
				|| (name.startsWith("java.lang.")
				&& !name.startsWith("java.lang.reflect.")
				&& !name.startsWith("java.lang.invoke."));
	}
}
