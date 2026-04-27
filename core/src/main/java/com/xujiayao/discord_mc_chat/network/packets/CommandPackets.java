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

	/**
	 * Packet group for the /console command flow.
	 */
	public static final class Console {
		private Console() {
		}

		/**
		 * Auto-complete request for /console command input.
		 */
		public static final class AutoCompleteRequestPacket extends Packet {
			/**
			 * Raw input to complete.
			 */
			public final String input;
			/**
			 * Requester's OP level.
			 */
			public final int opLevel;

			/**
			 * Creates an auto-complete request packet.
			 *
			 * @param input   Raw input to complete.
			 * @param opLevel Requester's OP level.
			 */
			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		/**
		 * Auto-complete response for /console command input.
		 */
		public static final class AutoCompleteResponsePacket extends Packet {
			/**
			 * Suggested completions.
			 */
			public final List<String> suggestions;

			/**
			 * Creates an auto-complete response packet.
			 *
			 * @param suggestions Suggested completions.
			 */
			public AutoCompleteResponsePacket(List<String> suggestions) {
				this.suggestions = suggestions;
			}
		}

		/**
		 * Request to execute a Minecraft command on a client.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Unique request identifier.
			 */
			public final String requestId;
			/**
			 * Requester's OP level.
			 */
			public final int opLevel;
			/**
			 * Full Minecraft command line.
			 */
			public final String commandLine;

			/**
			 * Creates a /console execution request packet.
			 *
			 * @param requestId   Unique request identifier.
			 * @param opLevel     Requester's OP level.
			 * @param commandLine Full Minecraft command line.
			 */
			public RequestPacket(String requestId, int opLevel, String commandLine) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.commandLine = commandLine;
			}
		}

		/**
		 * Response for a /console execution request.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Request identifier.
			 */
			public final String requestId;
			/**
			 * Command output.
			 */
			public final String response;

			/**
			 * Creates a /console execution response packet.
			 *
			 * @param requestId Request identifier.
			 * @param response  Command output.
			 */
			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
			}
		}
	}

	/**
	 * Packet group for the /execute command flow.
	 */
	public static final class Execute {
		private Execute() {
		}

		/**
		 * Auto-complete request for /execute command input.
		 */
		public static final class AutoCompleteRequestPacket extends Packet {
			/**
			 * Raw input to complete.
			 */
			public final String input;
			/**
			 * Requester's OP level.
			 */
			public final int opLevel;

			/**
			 * Creates an auto-complete request packet.
			 *
			 * @param input   Raw input to complete.
			 * @param opLevel Requester's OP level.
			 */
			public AutoCompleteRequestPacket(String input, int opLevel) {
				this.input = input;
				this.opLevel = opLevel;
			}
		}

		/**
		 * Auto-complete response for /execute command input.
		 */
		public static final class AutoCompleteResponsePacket extends Packet {
			/**
			 * Suggested completions.
			 */
			public final List<String> suggestions;

			/**
			 * Creates an auto-complete response packet.
			 *
			 * @param suggestions Suggested completions.
			 */
			public AutoCompleteResponsePacket(List<String> suggestions) {
				this.suggestions = suggestions;
			}
		}

		/**
		 * Request to execute a DMCC command on a client.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Unique request identifier.
			 */
			public final String requestId;
			/**
			 * Requester's OP level.
			 */
			public final int opLevel;
			/**
			 * DMCC command name.
			 */
			public final String command;
			/**
			 * DMCC command arguments.
			 */
			public final String[] args;

			/**
			 * Creates an /execute request packet.
			 *
			 * @param requestId Unique request identifier.
			 * @param opLevel   Requester's OP level.
			 * @param command   DMCC command name.
			 * @param args      DMCC command arguments.
			 */
			public RequestPacket(String requestId, int opLevel, String command, String... args) {
				this.requestId = requestId;
				this.opLevel = opLevel;
				this.command = command;
				this.args = args;
			}
		}

		/**
		 * Response for an /execute request.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Request identifier.
			 */
			public final String requestId;
			/**
			 * Command output.
			 */
			public final String response;
			/**
			 * Optional file data returned by command.
			 */
			public final byte[] fileData;
			/**
			 * Optional file name returned by command.
			 */
			public final String fileName;

			/**
			 * Creates an /execute response packet without file attachment.
			 *
			 * @param requestId Request identifier.
			 * @param response  Command output.
			 */
			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
				this.fileData = null;
				this.fileName = null;
			}

			/**
			 * Creates an /execute response packet with file attachment.
			 *
			 * @param requestId Request identifier.
			 * @param response  Command output.
			 * @param fileData  Optional file data returned by command.
			 * @param fileName  Optional file name returned by command.
			 */
			public ResponsePacket(String requestId, String response, byte[] fileData, String fileName) {
				this.requestId = requestId;
				this.response = response;
				this.fileData = fileData;
				this.fileName = fileName;
			}
		}
	}

	/**
	 * Packet group for the /update command flow.
	 */
	public static final class Update {
		private Update() {
		}

		/**
		 * Request to perform an update check on the connected DMCC Server.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Unique request identifier.
			 */
			public final String requestId;

			/**
			 * Creates an update check request packet.
			 *
			 * @param requestId Unique request identifier.
			 */
			public RequestPacket(String requestId) {
				this.requestId = requestId;
			}
		}

		/**
		 * Response for an update check request.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Unique request identifier.
			 */
			public final String requestId;
			/**
			 * Check result message.
			 */
			public final String response;

			/**
			 * Creates an update check response packet.
			 *
			 * @param requestId Request identifier.
			 * @param response  Result message.
			 */
			public ResponsePacket(String requestId, String response) {
				this.requestId = requestId;
				this.response = response;
			}
		}
	}

	/**
	 * Packet group for runtime info requests.
	 */
	public static final class Info {
		private Info() {
		}

		/**
		 * Request packet for runtime info.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Sender timestamp in milliseconds.
			 */
			public final long sentAtMillis;

			/**
			 * Creates a runtime info request packet.
			 *
			 * @param sentAtMillis Sender timestamp in milliseconds.
			 */
			public RequestPacket(long sentAtMillis) {
				this.sentAtMillis = sentAtMillis;
			}
		}

		/**
		 * Response packet for runtime info.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Current online player count.
			 */
			public final int onlinePlayerCount;
			/**
			 * Maximum player capacity.
			 */
			public final int maxPlayerCount;
			/**
			 * Per-player latency map.
			 */
			public final Map<String, Integer> playersAndLatencies;
			/**
			 * Number of players that have ever joined.
			 */
			public final int playersEverJoined;
			/**
			 * Server TPS metric.
			 */
			public final double tps;
			/**
			 * Server MSPT metric.
			 */
			public final double mspt;
			/**
			 * Uptime in seconds.
			 */
			public final long uptimeSeconds;
			/**
			 * JVM total memory in bytes.
			 */
			public final long totalMemory;
			/**
			 * JVM free memory in bytes.
			 */
			public final long freeMemory;
			/**
			 * Server name.
			 */
			public String serverName;
			/**
			 * Connection latency in milliseconds.
			 */
			public long connectionLatencyMillis;
			/**
			 * Minecraft version.
			 */
			public String minecraftVersion;

			/**
			 * Creates a runtime info response packet.
			 *
			 * @param serverName              Server name.
			 * @param connectionLatencyMillis Connection latency in milliseconds.
			 * @param minecraftVersion        Minecraft version.
			 * @param onlinePlayerCount       Current online player count.
			 * @param maxPlayerCount          Maximum player capacity.
			 * @param playersAndLatencies     Per-player latency map.
			 * @param playersEverJoined       Number of players that have ever joined.
			 * @param tps                     Server TPS metric.
			 * @param mspt                    Server MSPT metric.
			 * @param uptimeSeconds           Uptime in seconds.
			 * @param totalMemory             JVM total memory in bytes.
			 * @param freeMemory              JVM free memory in bytes.
			 */
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

	/**
	 * Packet group for account linking operations.
	 */
	public static final class Link {
		private Link() {
		}

		/**
		 * Request packet to create or verify a link code.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Minecraft player UUID.
			 */
			public final String minecraftUuid;
			/**
			 * Minecraft player name.
			 */
			public final String playerName;
			/**
			 * Whether this request is a join-time pre-check.
			 */
			public final boolean joinCheck;

			/**
			 * Creates a link request packet.
			 *
			 * @param minecraftUuid Minecraft player UUID.
			 * @param playerName    Minecraft player name.
			 * @param joinCheck     Whether this request is a join-time pre-check.
			 */
			public RequestPacket(String minecraftUuid, String playerName, boolean joinCheck) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
				this.joinCheck = joinCheck;
			}
		}

		/**
		 * Response packet for a link request.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Minecraft player UUID.
			 */
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

			/**
			 * Creates a link response packet.
			 *
			 * @param minecraftUuid Minecraft player UUID.
			 * @param code          Verification code, or empty when already linked.
			 * @param alreadyLinked Whether the player is already linked.
			 * @param discordName   Linked Discord display name when already linked.
			 */
			public ResponsePacket(String minecraftUuid, String code, boolean alreadyLinked, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.code = code;
				this.alreadyLinked = alreadyLinked;
				this.discordName = discordName;
			}
		}

		/**
		 * Packet carrying full OP level synchronization payload.
		 */
		public static final class OpSyncPacket extends Packet {
			/**
			 * Mapping of Minecraft UUID to OP level.
			 */
			public final Map<String, Integer> opLevels;

			/**
			 * Creates an OP sync packet.
			 *
			 * @param opLevels Mapping of Minecraft UUID to OP level.
			 */
			public OpSyncPacket(Map<String, Integer> opLevels) {
				this.opLevels = opLevels;
			}
		}
	}

	/**
	 * Packet group for unlink operations.
	 */
	public static final class Unlink {
		private Unlink() {
		}

		/**
		 * Request packet to unlink a player account.
		 */
		public static final class RequestPacket extends Packet {
			/**
			 * Minecraft player UUID.
			 */
			public final String minecraftUuid;
			/**
			 * Minecraft player name.
			 */
			public final String playerName;

			/**
			 * Creates an unlink request packet.
			 *
			 * @param minecraftUuid Minecraft player UUID.
			 * @param playerName    Minecraft player name.
			 */
			public RequestPacket(String minecraftUuid, String playerName) {
				this.minecraftUuid = minecraftUuid;
				this.playerName = playerName;
			}
		}

		/**
		 * Response packet for an unlink request.
		 */
		public static final class ResponsePacket extends Packet {
			/**
			 * Minecraft player UUID.
			 */
			public final String minecraftUuid;
			/**
			 * Whether unlink succeeded.
			 */
			public final boolean success;
			/**
			 * Previously linked Discord display name.
			 */
			public final String discordName;

			/**
			 * Creates an unlink response packet.
			 *
			 * @param minecraftUuid Minecraft player UUID.
			 * @param success       Whether unlink succeeded.
			 * @param discordName   Previously linked Discord display name.
			 */
			public ResponsePacket(String minecraftUuid, boolean success, String discordName) {
				this.minecraftUuid = minecraftUuid;
				this.success = success;
				this.discordName = discordName;
			}
		}
	}
}
