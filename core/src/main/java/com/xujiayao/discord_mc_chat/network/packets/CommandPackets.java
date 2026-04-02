package com.xujiayao.discord_mc_chat.network.packets;

import java.util.List;
import java.util.Map;

/**
 * Command-related packet group.
 *
 * @author Xujiayao
 */
public final class CommandPackets {
	private CommandPackets() {
	}

	public static final class Console {
		private Console() {
		}

		public static final class AutoCompleteRequestPacket extends Packet {
			public String input;
			public int opLevel;

			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		public static final class AutoCompleteResponsePacket extends Packet {
			public String serverName;
			public List<String> suggestions;

			public AutoCompleteResponsePacket(String serverName, List<String> suggestions) {
				this.serverName = serverName;
				this.suggestions = suggestions;
			}
		}

		public static final class RequestPacket extends Packet {
			public String requestId;
			public int opLevel;
			public String commandLine;

			public RequestPacket(String requestId, int opLevel, String commandLine) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.commandLine = commandLine;
			}
		}

		public static final class ResponsePacket extends Packet {
			public String requestId;
			public String response;

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
			public String input;
			public int opLevel;

			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		public static final class AutoCompleteResponsePacket extends Packet {
			public String serverName;
			public List<String> suggestions;

			public AutoCompleteResponsePacket(String serverName, List<String> suggestions) {
				this.serverName = serverName;
				this.suggestions = suggestions;
			}
		}

		public static final class RequestPacket extends Packet {
			public String requestId;
			public int opLevel;
			public String command;
			public String[] args;

			public RequestPacket(String requestId, int opLevel, String command, String... args) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.command = command;
				this.args = args;
			}
		}

		public static final class ResponsePacket extends Packet {
			public String requestId;
			public String response;
			public byte[] fileData;
			public String fileName;

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

	public static final class Info {
		private Info() {
		}

		public static final class RequestPacket extends Packet {
			public long sentAtMillis;

			public RequestPacket(long sentAtMillis) {
				this.sentAtMillis = sentAtMillis;
			}
		}

		public static final class ResponsePacket extends Packet {
			public String serverName;
			public long connectionLatencyMillis;
			public String minecraftVersion;
			public int onlinePlayerCount;
			public int maxPlayerCount;
			public Map<String, Integer> playersAndLatencies;
			public double tps;
			public double mspt;
			public long uptimeSeconds;
			public long totalMemory;
			public long freeMemory;

			public ResponsePacket(String serverName, long connectionLatencyMillis, String minecraftVersion,
			                      int onlinePlayerCount, int maxPlayerCount,
			                      Map<String, Integer> playersAndLatencies, double tps, double mspt,
			                      long uptimeSeconds, long totalMemory, long freeMemory) {
				this.serverName = serverName;
				this.connectionLatencyMillis = connectionLatencyMillis;
				this.minecraftVersion = minecraftVersion;
				this.onlinePlayerCount = onlinePlayerCount;
				this.maxPlayerCount = maxPlayerCount;
				this.playersAndLatencies = playersAndLatencies;
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
			public String minecraftUuid;
			public String playerName;
			public boolean joinCheck;

			public RequestPacket(String minecraftUuid, String playerName, boolean joinCheck) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
				this.joinCheck = joinCheck;
			}
		}

		public static final class ResponsePacket extends Packet {
			public String minecraftUuid;
			public String code;
			public boolean alreadyLinked;
			public String discordName;

			public ResponsePacket(String minecraftUuid, String code, boolean alreadyLinked, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.code = code;
				this.alreadyLinked = alreadyLinked;
				this.discordName = discordName;
			}
		}

		public static final class OpSyncPacket extends Packet {
			public Map<String, Integer> opLevels;

			public OpSyncPacket(Map<String, Integer> opLevels) {
				this.opLevels = opLevels;
			}
		}
	}

	public static final class Unlink {
		private Unlink() {
		}

		public static final class RequestPacket extends Packet {
			public String minecraftUuid;
			public String playerName;

			public RequestPacket(String minecraftUuid, String playerName) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
			}
		}

		public static final class ResponsePacket extends Packet {
			public String minecraftUuid;
			public boolean success;
			public String discordName;

			public ResponsePacket(String minecraftUuid, boolean success, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.success = success;
				this.discordName = discordName;
			}
		}
	}
}
