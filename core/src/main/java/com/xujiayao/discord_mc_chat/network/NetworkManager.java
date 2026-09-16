package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.protocol.Packet;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Central access point for sending network packets and managing connections.
 *
 * @author Xujiayao
 */
public final class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();

	// Server-side: Manage connected client channels
	private static final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();
	private static final Map<String, Long> clientConnectedAt = new ConcurrentHashMap<>();

	// Server-side: Info requests that are currently waiting for responses, keyed by request id. The id rides
	// on the wire (see Packets.InfoRequest/InfoSnapshot), so a response is only ever handed to the call that
	// asked for it. All access is guarded by infoLock.
	private static final Map<Long, InfoRound> pendingInfoRounds = new LinkedHashMap<>();
	private static final AtomicLong nextInfoRequestId = new AtomicLong();
	private static final AtomicReference<Supplier<Packets.InfoSnapshot>> infoSupplier = new AtomicReference<>();
	private static final Object infoLock = new Object();

	// DMCC command auto-complete cache
	private static final Map<String, List<String>> executeAutoCompleteCache = new ConcurrentHashMap<>();
	private static final Object executeAutoCompleteLock = new Object();

	// Minecraft command auto-complete cache
	private static final Map<String, List<String>> consoleAutoCompleteCache = new ConcurrentHashMap<>();
	private static final Object consoleAutoCompleteLock = new Object();

	private NetworkManager() {
	}

	public static void registerClient(ClientDMCC client) {
		clientInstance.set(client);
	}

	public static void registerInfoSupplier(Supplier<Packets.InfoSnapshot> supplier) {
		infoSupplier.set(supplier);
	}

	public static void clear() {
		clientInstance.set(null);
		clientChannels.clear();
		clientConnectedAt.clear();
		executeAutoCompleteCache.clear();
		consoleAutoCompleteCache.clear();
		synchronized (infoLock) {
			// Dropping the in-flight rounds also unblocks their callers: no client is left to answer, so
			// every waiting round is complete and returns the responses it already collected.
			pendingInfoRounds.clear();
			infoLock.notifyAll();
		}
	}

	public static void sendPacketToServer(Packet packet) {
		ClientDMCC client = clientInstance.get();
		if (client != null) {
			client.sendPacket(packet);
		}
	}

	public static void sendPacketToClient(Packet packet, String clientName) {
		clientChannels.forEach((channel, name) -> {
			if (clientName.equals(name)) {
				channel.writeAndFlush(packet);
			}
		});
	}

	public static void broadcastToClients(Packet packet) {
		clientChannels.forEach((channel, _) -> channel.writeAndFlush(packet));
	}

	public static void broadcastToClientsExcept(Packet packet, String excludedClientName) {
		clientChannels.forEach((channel, name) -> {
			if (!name.equals(excludedClientName)) {
				channel.writeAndFlush(packet);
			}
		});
	}

	public static void addClientChannel(Channel channel, String name) {
		clientChannels.put(channel, name);
		clientConnectedAt.put(name, System.currentTimeMillis());
	}

	public static void removeClientChannel(Channel channel) {
		String name = clientChannels.remove(channel);
		if (name != null) {
			clientConnectedAt.remove(name);
		}

		synchronized (infoLock) {
			// A client that just disconnected can no longer answer an in-flight info request, so wake the
			// waiting rounds: they recompute their awaited set and stop waiting for it.
			infoLock.notifyAll();
		}
	}

	public static List<String> getConnectedClientNames() {
		return new ArrayList<>(clientChannels.values());
	}

	public static boolean isClientConnected(String clientName) {
		return clientChannels.containsValue(clientName);
	}

	/**
	 * Gets the age of the current client connection in seconds.
	 *
	 * @param clientName The client name to check.
	 * @return The connection age in seconds, or Long.MAX_VALUE if the client is unknown.
	 */
	public static long getClientConnectionAgeSeconds(String clientName) {
		Long connectedAtMillis = clientConnectedAt.get(clientName);
		if (connectedAtMillis == null) {
			return Long.MAX_VALUE;
		}

		return TimeUnit.MILLISECONDS.toSeconds(Math.max(0L, System.currentTimeMillis() - connectedAtMillis));
	}

	/**
	 * Publishes a received InfoSnapshot to the round that asked for it.
	 * <p>
	 * A response whose request id matches no in-flight round is dropped: it can only be a leftover from a
	 * round that already returned, and recording it would either be ignored or, worse, be reported as the
	 * answer to a different question.
	 *
	 * @param clientName The client name
	 * @param packet     The received snapshot
	 */
	public static void cacheInfoResponse(String clientName, Packets.InfoSnapshot packet) {
		if (packet == null) {
			return;
		}

		String name = clientName;
		if (name == null || name.isBlank()) {
			name = packet.serverName();
		}
		if (name == null || name.isBlank()) {
			name = "unknown";
		}

		Packets.InfoSnapshot stored = packet.serverName() == null || packet.serverName().isBlank()
				? packet.withServerName(name)
				: packet;

		synchronized (infoLock) {
			InfoRound round = pendingInfoRounds.get(packet.requestId());
			if (round != null) {
				round.responses.put(name, stored);
				infoLock.notifyAll();
			}
		}
	}

	/**
	 * Sends InfoRequest packets, blocks the current thread, then returns the responses to this call.
	 * <p>
	 * The request id is echoed by every client, so a late response from an earlier round can no longer be
	 * mistaken for the answer to this one. The call still waits for the clients it broadcast to that are
	 * still connected: the awaited set is recomputed on every wake-up, so a client that disconnects can no
	 * longer make the caller block until the timeout.
	 *
	 * @param timeoutSeconds The waiting time in seconds
	 * @return The responses of this round, keyed by server name
	 */
	public static Map<String, Packets.InfoSnapshot> requestInfoSnapshot(int timeoutSeconds) {
		long requestId = nextInfoRequestId.incrementAndGet();
		InfoRound round = new InfoRound();

		synchronized (infoLock) {
			// Publish the round before the request goes out, so a response racing back to us is never missed.
			round.awaitedClientNames.addAll(clientChannels.values());
			pendingInfoRounds.put(requestId, round);
		}

		try {
			if (!round.awaitedClientNames.isEmpty()) {
				broadcastToClients(new Packets.InfoRequest(requestId, System.currentTimeMillis()));
			} else if (clientInstance.get() != null) {
				// Standalone/single-server fallback: answer locally from the registered supplier.
				Packets.InfoSnapshot localPacket = createResponsePacket();
				cacheInfoResponse(localPacket.serverName(), localPacket.withRequestId(requestId));
			}

			waitForInfoRound(round, timeoutSeconds);
		} finally {
			synchronized (infoLock) {
				pendingInfoRounds.remove(requestId);
			}
		}

		return new LinkedHashMap<>(round.responses);
	}

	/**
	 * Blocks the current thread until every client this round is waiting for has answered, or until the
	 * timeout expires.
	 * <p>
	 * Client connections are re-checked on each wake-up, so a disconnected client does not hold the caller
	 * for the remaining timeout. The completion check and the response bookkeeping share {@link #infoLock},
	 * which is also the monitor waited on here, so no response can slip in between the check and the wait.
	 *
	 * @param round          The round to wait for
	 * @param timeoutSeconds The waiting time in seconds
	 */
	private static void waitForInfoRound(InfoRound round, int timeoutSeconds) {
		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

		synchronized (infoLock) {
			while (!round.isComplete()) {
				long remaining = deadlineMillis - System.currentTimeMillis();
				if (remaining <= 0) {
					break;
				}
				try {
					infoLock.wait(remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	public static Packets.InfoSnapshot createResponsePacket() {
		Supplier<Packets.InfoSnapshot> supplier = infoSupplier.get();
		Packets.InfoSnapshot packet = supplier != null ? supplier.get() : null;

		if (packet == null) {
			Runtime runtime = Runtime.getRuntime();
			String serverName = getClientServerName();
			String minecraftVersion = EnvironmentUtils.isMinecraftEnvironment()
					? EnvironmentUtils.getMinecraftVersion()
					: "unknown";

			packet = new Packets.InfoSnapshot(
					0L,
					serverName,
					-1,
					minecraftVersion,
					0,
					0,
					Map.of(),
					0,
					0,
					0,
					0,
					runtime.totalMemory(),
					runtime.freeMemory()
			);
		}

		if (packet.serverName() == null || packet.serverName().isBlank()) {
			packet = packet.withServerName(getClientServerName());
		}

		if (packet.minecraftVersion() == null || packet.minecraftVersion().isBlank()) {
			packet = packet.withMinecraftVersion(EnvironmentUtils.isMinecraftEnvironment()
					? EnvironmentUtils.getMinecraftVersion()
					: "unknown");
		}

		return packet;
	}

	/**
	 * Correlation state of one {@link #requestInfoSnapshot(int)} call. All access happens while holding
	 * {@link #infoLock}.
	 */
	private static final class InfoRound {
		/**
		 * Client names this round broadcast its request to. A name that is no longer connected is skipped by
		 * {@link #isComplete()}, so it cannot keep the caller waiting for a response that will never come.
		 */
		private final Set<String> awaitedClientNames = new LinkedHashSet<>();

		/**
		 * Latest response per server name, in arrival order.
		 */
		private final Map<String, Packets.InfoSnapshot> responses = new LinkedHashMap<>();

		private boolean isComplete() {
			for (String clientName : awaitedClientNames) {
				if (!responses.containsKey(clientName) && clientChannels.containsValue(clientName)) {
					return false;
				}
			}
			return true;
		}
	}

	// ===== DMCC Command Auto-Complete Methods =====

	public static void cacheExecuteAutoCompleteResponse(String clientName, List<String> suggestions) {
		executeAutoCompleteCache.put(clientName, suggestions);
		synchronized (executeAutoCompleteLock) {
			executeAutoCompleteLock.notifyAll();
		}
	}

	public static Map<String, List<String>> requestExecuteAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				executeAutoCompleteCache,
				executeAutoCompleteLock,
				new Packets.AutoCompleteRequest(Packets.RpcKind.EXECUTE, input, opLevel),
				timeoutSeconds
		);
	}

	// ===== Minecraft Command Auto-Complete Methods =====

	public static void cacheConsoleAutoCompleteResponse(String clientName, List<String> suggestions) {
		consoleAutoCompleteCache.put(clientName, suggestions);
		synchronized (consoleAutoCompleteLock) {
			consoleAutoCompleteLock.notifyAll();
		}
	}

	public static Map<String, List<String>> requestConsoleAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				consoleAutoCompleteCache,
				consoleAutoCompleteLock,
				new Packets.AutoCompleteRequest(Packets.RpcKind.CONSOLE, input, opLevel),
				timeoutSeconds
		);
	}

	private static Map<String, List<String>> requestAutoCompleteSnapshot(Map<String, List<String>> cache,
																		 Object lock,
																		 Packet requestPacket,
																		 int timeoutSeconds) {
		cache.clear();

		int expectedResponses = clientChannels.size();
		if (expectedResponses > 0) {
			broadcastToClients(requestPacket);

			long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
			synchronized (lock) {
				while (cache.size() < expectedResponses) {
					long remaining = deadlineMillis - System.currentTimeMillis();
					if (remaining <= 0) {
						break;
					}
					try {
						lock.wait(remaining);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		Map<String, List<String>> snapshot = new LinkedHashMap<>(cache);
		cache.clear();
		return snapshot;
	}

	// ===== Client Accessors =====

	public static ClientDMCC getClient() {
		return clientInstance.get();
	}

	public static String getClientServerName() {
		ClientDMCC client = clientInstance.get();
		if (client == null) {
			return "unknown";
		}
		return client.getServerName();
	}
}
