package com.xujiayao.discord_mc_chat.network.packets;

import java.util.List;
import java.util.Map;

/**
 * @author Xujiayao
 */
public final class CommandPackets {
	private CommandPackets() {
	}

	public static final class Console {
		private Console() {
		}

		public static final class AutoCompleteRequestPacket extends Packet {
			public final String input;
			public final int opLevel;

			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		public static final class AutoCompleteResponsePacket extends Packet {
			public final List<String> suggestions;

			public AutoCompleteResponsePacket(List<String> suggestions) {
				this.suggestions = suggestions;
			}
		}

		public static final class RequestPacket extends Packet {
			public final String requestId;
			public final int opLevel;
			public final String commandLine;

			public RequestPacket(String requestId, int opLevel, String commandLine) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.commandLine = commandLine;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final String requestId;
			public final String response;

			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
			}
		}
	}

	public static final class Execute {
		private Execute() {
		}

		public static final class AutoCompleteRequestPacket extends Packet {
			public final String input;
			public final int opLevel;

			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		public static final class AutoCompleteResponsePacket extends Packet {
			public final List<String> suggestions;

			public AutoCompleteResponsePacket(List<String> suggestions) {
				this.suggestions = suggestions;
			}
		}

		public static final class RequestPacket extends Packet {
			public final String requestId;
			public final int opLevel;
			public final String command;
			public final String[] args;

			public RequestPacket(String requestId, int opLevel, String command, String... args) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.command = command;
				this.args = args;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final String requestId;
			public final String response;
			public final byte[] fileData;
			public final String fileName;

			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
				this.fileData = null;
				this.fileName = null;
			}

			public ResponsePacket(String requestId, String response, byte[] fileData, String fileName) {
				this.requestId = requestId;
				this.response = response;
				this.fileData = fileData;
				this.fileName = fileName;
			}
		}
	}

	public static final class Update {
		private Update() {
		}

		public static final class RequestPacket extends Packet {
			public final String requestId;

			public RequestPacket(String requestId) {
				this.requestId = requestId;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final String requestId;
			public final String response;

			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
			}
		}
	}

	public static final class Info {
		private Info() {
		}

		public static final class RequestPacket extends Packet {
			/**
			 * Sender timestamp in milliseconds.
			 */
			public final long sentAtMillis;

			public RequestPacket(long sentAtMillis) {
				this.sentAtMillis = sentAtMillis;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final int onlinePlayerCount;
			public final int maxPlayerCount;
			public final Map<String, Integer> playersAndLatencies;
			public final int playersEverJoined;
			public final double tps;
			public final double mspt;
			public final long uptimeSeconds;
			public final long totalMemory;
			public final long freeMemory;
			public String serverName;
			public long connectionLatencyMillis;
			public String minecraftVersion;

			public ResponsePacket(String serverName, long connectionLatencyMillis, String minecraftVersion,
			                      int onlinePlayerCount, int maxPlayerCount,
			                      Map<String, Integer> playersAndLatencies, int playersEverJoined, double tps, double mspt,
			                      long uptimeSeconds, long totalMemory, long freeMemory) {
				this.serverName = serverName;
				this.connectionLatencyMillis = connectionLatencyMillis;
				this.minecraftVersion = minecraftVersion;
				this.onlinePlayerCount = onlinePlayerCount;
				this.maxPlayerCount = maxPlayerCount;
				this.playersAndLatencies = playersAndLatencies;
				this.playersEverJoined = playersEverJoined;
				this.tps = tps;
				this.mspt = mspt;
				this.uptimeSeconds = uptimeSeconds;
				this.totalMemory = totalMemory;
				this.freeMemory = freeMemory;
			}
		}
	}

	public static final class Link {
		private Link() {
		}

		public static final class RequestPacket extends Packet {
			public final String minecraftUuid;
			public final String playerName;
			/**
			 * Whether this request is a join-time pre-check.
			 */
			public final boolean joinCheck;

			public RequestPacket(String minecraftUuid, String playerName, boolean joinCheck) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
				this.joinCheck = joinCheck;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final String minecraftUuid;
			/**
			 * Verification code, or empty when already linked.
			 */
			public final String code;
			/**
			 * Whether the player is already linked.
			 */
			public final boolean alreadyLinked;
			/**
			 * Linked Discord display name when already linked.
			 */
			public final String discordName;

			public ResponsePacket(String minecraftUuid, String code, boolean alreadyLinked, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.code = code;
				this.alreadyLinked = alreadyLinked;
				this.discordName = discordName;
			}
		}

		public static final class OpSyncPacket extends Packet {
			/**
			 * Mapping of Minecraft UUID to OP level.
			 */
			public final Map<String, Integer> opLevels;

			public OpSyncPacket(Map<String, Integer> opLevels) {
				this.opLevels = opLevels;
			}
		}
	}

	public static final class Unlink {
		private Unlink() {
		}

		public static final class RequestPacket extends Packet {
			public final String minecraftUuid;
			public final String playerName;

			public RequestPacket(String minecraftUuid, String playerName) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
			}
		}

		public static final class ResponsePacket extends Packet {
			public final String minecraftUuid;
			/**
			 * Whether unlink succeeded.
			 */
			public final boolean success;
			/**
			 * Previously linked Discord display name.
			 */
			public final String discordName;

			public ResponsePacket(String minecraftUuid, boolean success, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.success = success;
				this.discordName = discordName;
			}
		}
	}
}
