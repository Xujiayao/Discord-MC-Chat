package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.protocol.Packet;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
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

	// Server-side: Info requests that are currently waiting for responses, keyed by an internally generated
	// request id. The id never leaves this class; it only gives every concurrent caller its own state.
	// All access is guarded by infoLock.
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

	/**
	 * Registers the client instance for network operations.
	 *
	 * @param client The client instance
	 */
	public static void registerClient(ClientDMCC client) {
		clientInstance.set(client);
	}

	/**
	 * Registers an information supplier for InfoResponse packets.
	 *
	 * @param supplier The supplier to register
	 */
	public static void registerInfoSupplier(Supplier<Packets.InfoSnapshot> supplier) {
		infoSupplier.set(supplier);
	}

	/**
	 * Resets the network manager state, clearing client instance and channels.
	 */
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

	/**
	 * Sends a packet to the server. Should be called from client only.
	 *
	 * @param packet The packet to send
	 */
	public static void sendPacketToServer(Packet packet) {
		ClientDMCC client = clientInstance.get();
		if (client != null) {
			client.sendPacket(packet);
		}
	}

	/**
	 * Sends a packet to a specific connected client. Should be called from server only.
	 *
	 * @param packet     The packet to send
	 * @param clientName The name of the target client
	 */
	public static void sendPacketToClient(Packet packet, String clientName) {
		clientChannels.forEach((channel, name) -> {
			if (clientName.equals(name)) {
				channel.writeAndFlush(packet);
			}
		});
	}

	/**
	 * Broadcasts a packet to all connected clients. Should be called from server only.
	 *
	 * @param packet The packet to send
	 */
	public static void broadcastToClients(Packet packet) {
		clientChannels.forEach((channel, _) -> channel.writeAndFlush(packet));
	}

	/**
	 * Broadcasts a packet to all connected clients except the excluded client.
	 * Should be called from server only.
	 *
	 * @param packet             The packet to send
	 * @param excludedClientName The client name to exclude
	 */
	public static void broadcastToClientsExcept(Packet packet, String excludedClientName) {
		clientChannels.forEach((channel, name) -> {
			if (!name.equals(excludedClientName)) {
				channel.writeAndFlush(packet);
			}
		});
	}

	/**
	 * Adds a client channel to the managed list.
	 *
	 * @param channel The client channel
	 * @param name    The name of the client
	 */
	public static void addClientChannel(Channel channel, String name) {
		clientChannels.put(channel, name);
		clientConnectedAt.put(name, System.currentTimeMillis());
	}

	/**
	 * Removes a client channel from the managed list.
	 *
	 * @param channel The client channel
	 */
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

	/**
	 * Gets all connected client names.
	 *
	 * @return A list of connected client names.
	 */
	public static List<String> getConnectedClientNames() {
		return new ArrayList<>(clientChannels.values());
	}

	/**
	 * Checks if a client is connected by name.
	 *
	 * @param clientName The client name to check.
	 * @return true if connected, false otherwise.
	 */
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
	 * Gets the remote address of a channel as a string (IP:Port).
	 *
	 * @param channel The channel
	 * @return The remote address string
	 */
	public static String getRemoteAddress(Channel channel) {
		if (channel.remoteAddress() instanceof InetSocketAddress addr) {
			return addr.getAddress().getHostAddress() + ":" + addr.getPort();
		}
		return channel.remoteAddress().toString();
	}

	// ===== Info Methods =====

	/**
	 * Publishes a received InfoSnapshot to every info request that is currently waiting for responses.
	 * <p>
	 * Responses are broadcast to the in-flight rounds instead of being parked in one shared cache, so each
	 * caller keeps its own result map and no caller can clear, overwrite or consume another caller's data.
	 *
	 * @param clientName The client name
	 * @param packet     The packet to cache
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
			for (InfoRound round : pendingInfoRounds.values()) {
				round.accept(name, stored);
			}
			infoLock.notifyAll();
		}
	}

	/**
	 * Sends InfoRequest packets, blocks the current thread, then returns a snapshot of the responses that
	 * belong to this call.
	 * <p>
	 * <b>Correlation:</b> {@link Packets.InfoSnapshot} carries no echo of the {@code sentAtMillis} that
	 * {@link Packets.InfoRequest} was sent with (the client only uses it locally to compute
	 * {@code connectionLatencyMillis}), so a response cannot be matched to the request that produced it on
	 * the wire, and changing that would mean changing the packet shape. This method therefore correlates
	 * round-scoped: every call registers its own {@link InfoRound} under an internally generated request id
	 * before the request is broadcast, and unregisters it when it returns. A response is recorded by every
	 * round that is in flight when it arrives, with the latest value winning per server name, so a response
	 * left over from an older round is replaced by the actual answer to this one as soon as it arrives.
	 * <p>
	 * A round only waits for clients that it broadcast to and that are still connected: the awaited set is
	 * recomputed on every wake-up, so a client that disconnects (or one that connects afterwards) can no
	 * longer make the caller block until the timeout. The request timestamp is still generated fresh per
	 * call, so the client-side latency measurement is unchanged.
	 *
	 * @param timeoutSeconds The waiting time in seconds
	 * @return A snapshot of cached ResponsePacket items
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
				broadcastToClients(new Packets.InfoRequest(System.currentTimeMillis()));
			} else if (clientInstance.get() != null) {
				// Standalone/single-server fallback: answer locally from the registered supplier.
				Packets.InfoSnapshot localPacket = createResponsePacket();
				cacheInfoResponse(localPacket.serverName(), localPacket);
			}

			waitForInfoRound(round, timeoutSeconds);
		} finally {
			synchronized (infoLock) {
				pendingInfoRounds.remove(requestId);
			}
		}

		return round.snapshot();
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

	/**
	 * Creates an ResponsePacket using the registered supplier or a fallback.
	 *
	 * @return The ResponsePacket instance
	 */
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
	 * Correlation state of one {@link #requestInfoSnapshot(int)} call.
	 * <p>
	 * All access happens while holding {@link #infoLock}, which is what lets the collections below stay plain
	 * (non-concurrent) collections: a caller either owns the lock, or it is inside {@link #waitForInfoRound}
	 * where the monitor is released only while waiting for a response to be published.
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

		/**
		 * Client names that answered at least once, so repeat answers do not extend the wait.
		 */
		private final Set<String> answeredClientNames = new HashSet<>();

		/**
		 * Records one response. The latest value wins, so a response left over from an older round is
		 * replaced by the answer to this round as soon as it arrives.
		 *
		 * @param clientName The server name the response is attributed to
		 * @param packet     The received snapshot
		 */
		private void accept(String clientName, Packets.InfoSnapshot packet) {
			responses.put(clientName, packet);
			answeredClientNames.add(clientName);
		}

		/**
		 * @return true when every client this round is still waiting for is either answered or disconnected.
		 */
		private boolean isComplete() {
			for (String clientName : awaitedClientNames) {
				if (!answeredClientNames.contains(clientName) && clientChannels.containsValue(clientName)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * @return The collected responses, in the same shape the shared cache used to be returned in.
		 */
		private Map<String, Packets.InfoSnapshot> snapshot() {
			return new LinkedHashMap<>(responses);
		}
	}

	// ===== DMCC Command Auto-Complete Methods =====

	/**
	 * Caches an auto-complete response from a client for DMCC commands.
	 *
	 * @param clientName  The client name
	 * @param suggestions The list of suggestions
	 */
	public static void cacheExecuteAutoCompleteResponse(String clientName, List<String> suggestions) {
		executeAutoCompleteCache.put(clientName, suggestions);
		synchronized (executeAutoCompleteLock) {
			executeAutoCompleteLock.notifyAll();
		}
	}

	/**
	 * Requests DMCC command auto-complete suggestions from all connected clients.
	 *
	 * @param input          The current user input to complete
	 * @param opLevel        The OP level of the user requesting auto-complete
	 * @param timeoutSeconds The waiting time in seconds
	 * @return A map of client name to suggestion list
	 */
	public static Map<String, List<String>> requestExecuteAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				executeAutoCompleteCache,
				new Packets.AutoCompleteRequest(Packets.RpcKind.EXECUTE, input, opLevel),
				timeoutSeconds,
				true
		);
	}

	// ===== Minecraft Command Auto-Complete Methods =====

	/**
	 * Caches an auto-complete response from a client for Minecraft commands.
	 *
	 * @param clientName  The client name
	 * @param suggestions The list of suggestions
	 */
	public static void cacheConsoleAutoCompleteResponse(String clientName, List<String> suggestions) {
		consoleAutoCompleteCache.put(clientName, suggestions);
		synchronized (consoleAutoCompleteLock) {
			consoleAutoCompleteLock.notifyAll();
		}
	}

	/**
	 * Requests Minecraft command auto-complete suggestions from all connected clients.
	 *
	 * @param input          The current user input to complete
	 * @param opLevel        The OP level of the user requesting auto-complete
	 * @param timeoutSeconds The waiting time in seconds
	 * @return A map of client name to suggestion list
	 */
	public static Map<String, List<String>> requestConsoleAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				consoleAutoCompleteCache,
				new Packets.AutoCompleteRequest(Packets.RpcKind.CONSOLE, input, opLevel),
				timeoutSeconds,
				false
		);
	}

	private static Map<String, List<String>> requestAutoCompleteSnapshot(Map<String, List<String>> cache,
																		 Packet requestPacket,
																		 int timeoutSeconds,
																		 boolean executeRequest) {
		cache.clear();

		int expectedResponses = clientChannels.size();
		if (expectedResponses > 0) {
			broadcastToClients(requestPacket);
		}

		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

		if (expectedResponses > 0) {
			if (executeRequest) {
				synchronized (executeAutoCompleteLock) {
					while (cache.size() < expectedResponses) {
						long remaining = deadlineMillis - System.currentTimeMillis();
						if (remaining <= 0) break;
						try {
							executeAutoCompleteLock.wait(remaining);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			} else {
				synchronized (consoleAutoCompleteLock) {
					while (cache.size() < expectedResponses) {
						long remaining = deadlineMillis - System.currentTimeMillis();
						if (remaining <= 0) break;
						try {
							consoleAutoCompleteLock.wait(remaining);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			}
		}

		Map<String, List<String>> snapshot = new LinkedHashMap<>(cache);
		cache.clear();
		return snapshot;
	}

	// ===== Client Accessors =====

	/**
	 * Gets the registered client instance.
	 *
	 * @return The client instance, or null if not registered
	 */
	public static ClientDMCC getClient() {
		return clientInstance.get();
	}

	/**
	 * Gets the client server name if available.
	 *
	 * @return The client server name, or "unknown"
	 */
	public static String getClientServerName() {
		ClientDMCC client = clientInstance.get();
		if (client == null) {
			return "unknown";
		}
		return client.getServerName();
	}
}
