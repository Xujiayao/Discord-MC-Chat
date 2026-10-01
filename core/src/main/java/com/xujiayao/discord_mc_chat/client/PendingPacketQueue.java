package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.ConsoleLogBatchPacket;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Buffers relay packets that could not be written while the DMCC server was unreachable, and replays them in
 * order after the next successful login.
 * <p>
 * The buffer is static on purpose: {@code NettyClient} and {@code ClientHandler} are recreated by every
 * {@code /dmcc reload}, and surviving that recreation is the point of this class - the same reason
 * {@code ConsoleLogTailer} keeps its pending lines in a static field. Only packets that describe Minecraft
 * activity are buffered; requests, responses and handshake traffic must never be replayed late.
 * <p>
 * The replay writes one packet at a time and paces itself per batch, so catching up after an outage cannot
 * flood Discord's REST buckets. A write that fails is put back at the head of the buffer, which keeps the
 * replay in order.
 *
 * @author Xujiayao
 */
public final class PendingPacketQueue {

	// Upper bound for the buffer, so a long outage cannot exhaust the heap.
	private static final int MAX_PENDING_PACKETS = 2000;
	// Replay pacing: at most MAX_PACKETS_PER_BATCH packets every DRAIN_INTERVAL_MILLIS.
	private static final int MAX_PACKETS_PER_BATCH = 5;
	private static final long DRAIN_INTERVAL_MILLIS = 250;
	// Queue-state warnings (buffered, queue full) are collapsed to at most one per interval.
	private static final long WARN_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60);

	private static final Deque<Entry> queue = new ArrayDeque<>();
	private static final AtomicLong lastQueuedWarnMillis = new AtomicLong(0);
	private static final AtomicLong lastDroppedWarnMillis = new AtomicLong(0);
	// Guarded by queue: replay counters for the drain currently in progress.
	private static int drainedSinceLogin;
	private static long drainStartedAtMillis;
	private static volatile Channel drainChannel;
	private static volatile String identity = "";

	private PendingPacketQueue() {
	}

	/**
	 * Buffers a packet that could not be sent, so it can be replayed after the next successful login. Packets
	 * that must not be replayed late are dropped with the usual warning instead.
	 *
	 * @param packet The packet that could not be sent.
	 * @return {@code true} if the packet was buffered, {@code false} if it was dropped.
	 */
	public static boolean enqueue(Packet packet) {
		// Losing a relay packet on a disconnect means losing a chat line, a join/quit notice or a console
		// batch forever, so those are kept. Requests and responses carry a caller that is already timing out
		// or a state that is stale by now, so they keep the old "drop and warn" behaviour.
		if (!(packet instanceof MinecraftEventPacket) && !(packet instanceof ConsoleLogBatchPacket)) {
			LOGGER.warn(I18nManager.getDmccTranslation("client.network.send_while_disconnected", packet.getClass().getSimpleName()));
			return false;
		}

		synchronized (queue) {
			if (queue.size() >= MAX_PENDING_PACKETS) {
				queue.removeFirst();
				warnAtMostOncePerInterval(lastDroppedWarnMillis, "client.pending_queue.dropped_full", MAX_PENDING_PACKETS);
			}
			queue.addLast(new Entry(packet));
			warnAtMostOncePerInterval(lastQueuedWarnMillis, "client.pending_queue.queued", queue.size());
		}

		return true;
	}

	/**
	 * Starts replaying the buffer on the connection that just authenticated.
	 *
	 * @param channel The authenticated channel to replay into.
	 */
	static void onAuthenticated(Channel channel) {
		drainChannel = channel;

		synchronized (queue) {
			drainStartedAtMillis = System.currentTimeMillis();
		}

		channel.eventLoop().execute(() -> drainBatch(channel, MAX_PACKETS_PER_BATCH));
	}

	/**
	 * Marks the current connection as gone. The buffer itself is kept for the next connection.
	 */
	static void onConnectionLost() {
		drainChannel = null;
		// A new outage starts here, so the next buffered packet may log again.
		lastQueuedWarnMillis.set(0);
	}

	/**
	 * Drops the buffer when the configured DMCC server changes, because the buffered packets were produced for
	 * the old server address.
	 *
	 * @param host       DMCC server host.
	 * @param port       DMCC server port.
	 * @param serverName Logical client name.
	 */
	static void onClientIdentity(String host, int port, String serverName) {
		String newIdentity = host + ":" + port + "/" + serverName;
		if (newIdentity.equals(identity)) {
			return;
		}
		identity = newIdentity;

		int dropped = clear();
		if (dropped > 0) {
			LOGGER.warn(I18nManager.getDmccTranslation("client.pending_queue.discarded_identity_changed", dropped));
		}
	}

	/**
	 * Drops the buffer because the client is shutting down for good, not as part of a reload.
	 */
	static void discard() {
		drainChannel = null;

		int dropped = clear();
		if (dropped > 0) {
			LOGGER.info(I18nManager.getDmccTranslation("client.pending_queue.discarded_shutdown", dropped));
		}
	}

	private static int clear() {
		synchronized (queue) {
			int dropped = queue.size();
			queue.clear();
			drainedSinceLogin = 0;
			drainStartedAtMillis = 0;
			return dropped;
		}
	}

	private static void drainBatch(Channel channel, int remaining) {
		// A reload or a reconnect replaces the channel, and the old drain must not touch the new one.
		if (channel != drainChannel || !channel.isActive()) {
			return;
		}

		if (!channel.isWritable()) {
			channel.eventLoop().schedule(() -> drainBatch(channel, remaining), DRAIN_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
			return;
		}

		if (remaining <= 0) {
			// Batch budget spent: wait one interval before the next batch.
			channel.eventLoop().schedule(() -> drainBatch(channel, MAX_PACKETS_PER_BATCH), DRAIN_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
			return;
		}

		Entry entry = pollNext();
		if (entry == null) {
			finishDrain(channel);
			return;
		}

		channel.writeAndFlush(entry.packet).addListener((ChannelFuture future) -> {
			if (!future.isSuccess()) {
				// Back to the head, so the packet stays in front of everything that is still waiting.
				synchronized (queue) {
					queue.addFirst(entry);
				}
				LOGGER.warn(I18nManager.getDmccTranslation("client.pending_queue.send_failed",
						future.cause() == null ? "" : future.cause().getMessage()));
				channel.eventLoop().schedule(() -> drainBatch(channel, MAX_PACKETS_PER_BATCH), DRAIN_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
				return;
			}

			synchronized (queue) {
				drainedSinceLogin++;
			}
			drainBatch(channel, remaining - 1);
		});
	}

	private static Entry pollNext() {
		synchronized (queue) {
			return queue.isEmpty() ? null : queue.removeFirst();
		}
	}

	private static void finishDrain(Channel channel) {
		if (channel != drainChannel) {
			return;
		}

		int drained;
		long elapsedMillis;
		synchronized (queue) {
			drained = drainedSinceLogin;
			elapsedMillis = Math.max(0, System.currentTimeMillis() - drainStartedAtMillis);
			drainedSinceLogin = 0;
		}

		lastQueuedWarnMillis.set(0);
		if (drained > 0) {
			LOGGER.info(I18nManager.getDmccTranslation("client.pending_queue.drained", drained, elapsedMillis));
		}
	}

	private static void warnAtMostOncePerInterval(AtomicLong lastWarnMillis, String translationKey, Object... args) {
		long now = System.currentTimeMillis();
		long previous = lastWarnMillis.get();
		if (now - previous < WARN_INTERVAL_MILLIS || !lastWarnMillis.compareAndSet(previous, now)) {
			return;
		}
		LOGGER.warn(I18nManager.getDmccTranslation(translationKey, args));
	}

	private static final class Entry {
		private final Packet packet;

		private Entry(Packet packet) {
			this.packet = packet;
		}
	}
}
