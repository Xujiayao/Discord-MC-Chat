package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Miscellaneous packet group.
 *
 * @author Xujiayao
 */
public final class MiscPackets {
	private MiscPackets() {
	}

	/**
	 * Keepalive heartbeat packet.
	 */
	public static final class KeepAlivePacket extends Packet {
		/**
		 * Creates a keepalive heartbeat packet.
		 */
		public KeepAlivePacket() {
		}
	}

	/**
	 * Active latency probe request packet.
	 */
	public static final class LatencyPingPacket extends Packet {
		/**
		 * Sender timestamp in milliseconds.
		 */
		public long sentAtMillis;

		/**
		 * Creates a latency ping packet.
		 *
		 * @param sentAtMillis Sender timestamp in milliseconds.
		 */
		public LatencyPingPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}

	/**
	 * Active latency probe response packet.
	 */
	public static final class LatencyPongPacket extends Packet {
		/**
		 * Echoed sender timestamp in milliseconds.
		 */
		public long sentAtMillis;

		/**
		 * Creates a latency pong packet.
		 *
		 * @param sentAtMillis Echoed sender timestamp in milliseconds.
		 */
		public LatencyPongPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}
}
