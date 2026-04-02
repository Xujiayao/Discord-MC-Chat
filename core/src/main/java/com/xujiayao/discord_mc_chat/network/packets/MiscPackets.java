package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Miscellaneous packet group.
 *
 * @author Xujiayao
 */
public final class MiscPackets {
	private MiscPackets() {
	}

	public static final class KeepAlivePacket extends Packet {
	}

	public static final class LatencyPingPacket extends Packet {
		public long sentAtMillis;

		public LatencyPingPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}

	public static final class LatencyPongPacket extends Packet {
		public long sentAtMillis;

		public LatencyPongPacket(long sentAtMillis) {
			this.sentAtMillis = sentAtMillis;
		}
	}
}

