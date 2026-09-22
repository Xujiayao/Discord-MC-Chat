package com.xujiayao.discord_mc_chat.network.packets;

/**
 * @author Xujiayao
 */
public final class MiscPackets {
	private MiscPackets() {
	}

	public static final class KeepAlivePacket extends Packet {
		public KeepAlivePacket() {
		}
	}

	public static final class LatencyPingPacket extends Packet {
		/**
		 * Sender timestamp in milliseconds.
		 */
		public final long sentAtMillis;

		public LatencyPingPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}

	public static final class LatencyPongPacket extends Packet {
		/**
		 * Echoed sender timestamp in milliseconds.
		 */
		public final long sentAtMillis;

		public LatencyPongPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}
}
