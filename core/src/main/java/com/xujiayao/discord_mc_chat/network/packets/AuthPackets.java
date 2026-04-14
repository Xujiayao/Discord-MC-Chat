package com.xujiayao.discord_mc_chat.network.packets;

/**
 * Authentication packet group.
 *
 * @author Xujiayao
 */
public final class AuthPackets {
	private AuthPackets() {
	}

	/**
	 * Sent by client with the calculated hash.
	 */
	public static final class AuthResponsePacket extends Packet {
		public String hash;

		public AuthResponsePacket(String hash) {
			this.hash = hash;
		}
	}

	/**
	 * Sent by server to challenge the client.
	 */
	public static final class ChallengePacket extends Packet {
		public String salt;

		public ChallengePacket(String salt) {
			this.salt = salt;
		}
	}

	/**
	 * Sent by either side to disconnect with a localized reason.
	 */
	public static final class DisconnectPacket extends Packet {
		public String key;
		public Object[] args;

		public DisconnectPacket(String key, Object... args) {
			this.key = key;
			this.args = args;
		}
	}

	/**
	 * Sent by client to initiate connection.
	 */
	public static final class HandshakePacket extends Packet {
		public String serverName;
		public String dmccVersion;
		public String minecraftVersion;

		public HandshakePacket(String serverName, String dmccVersion, String minecraftVersion) {
			this.serverName = serverName;
			this.dmccVersion = dmccVersion;
			this.minecraftVersion = minecraftVersion;
		}
	}

	/**
	 * Sent by server to confirm authentication success.
	 */
	public static final class LoginSuccessPacket extends Packet {
		public String language;
		public boolean overwriteMinecraftSourceMessages;
		public boolean consoleForwardingEnabled;

		public LoginSuccessPacket(String language, boolean overwriteMinecraftSourceMessages, boolean consoleForwardingEnabled) {
			this.language = language;
			this.overwriteMinecraftSourceMessages = overwriteMinecraftSourceMessages;
			this.consoleForwardingEnabled = consoleForwardingEnabled;
		}
	}
}

