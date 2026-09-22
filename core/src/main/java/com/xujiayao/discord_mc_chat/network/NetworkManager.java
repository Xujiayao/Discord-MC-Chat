package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author Xujiayao
 */
public final class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();

	// Server-side: Manage connected client channels
	private static final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();
	private static final Map<String, Long> clientConnectedAt = new ConcurrentHashMap<>();

	private static final AtomicReference<Supplier<CommandPackets.Info.ResponsePacket>> infoSupplier = new AtomicReference<>();

	// One entry per in-flight snapshot request, so that concurrent requests cannot clear or consume each
	// other's responses
	private static final Set<SnapshotRequest<CommandPackets.Info.ResponsePacket>> infoRequests = ConcurrentHashMap.newKeySet();
	private static final Set<SnapshotRequest<List<String>>> executeAutoCompleteRequests = ConcurrentHashMap.newKeySet();
	private static final Set<SnapshotRequest<List<String>>> consoleAutoCompleteRequests = ConcurrentHashMap.newKeySet();

	private NetworkManager() {
	}

	public static void registerClient(ClientDMCC client) {
		clientInstance.set(client);
	}

	public static void registerInfoSupplier(Supplier<CommandPackets.Info.ResponsePacket> supplier) {
		infoSupplier.set(supplier);
	}

	public static void clear() {
		clientInstance.set(null);
		clientChannels.clear();
		clientConnectedAt.clear();
		// Wake in-flight snapshot requests so a shutdown does not make them sit out their remaining timeout
		dropAwaitingClients(infoRequests);
		dropAwaitingClients(executeAutoCompleteRequests);
		dropAwaitingClients(consoleAutoCompleteRequests);
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

	public static void addClientChannel(Channel channel, String name) {
		clientChannels.put(channel, name);
		clientConnectedAt.put(name, System.currentTimeMillis());
	}

	public static void removeClientChannel(Channel channel) {
		String name = clientChannels.remove(channel);
		if (name == null) {
			return;
		}

		clientConnectedAt.remove(name);

		// A disconnected client can never answer an in-flight snapshot request, so forget it and wake the
		// waiters up instead of letting them wait for the timeout
		forgetAwaitingClient(infoRequests, name);
		forgetAwaitingClient(executeAutoCompleteRequests, name);
		forgetAwaitingClient(consoleAutoCompleteRequests, name);
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

	public static String getRemoteAddress(Channel channel) {
		// Read the address once: a closed channel reports none at all, which used to throw here
		SocketAddress remoteAddress = channel.remoteAddress();
		if (remoteAddress instanceof InetSocketAddress addr) {
			return addr.getAddress().getHostAddress() + ":" + addr.getPort();
		}
		return remoteAddress != null ? remoteAddress.toString() : "unknown";
	}

	// ===== Snapshot Request Methods =====

	/**
	 * The response container and wait monitor of a single in-flight snapshot request. Every requester owns
	 * one of these, so concurrent requests can no longer clear or steal each other's responses.
	 */
	private static final class SnapshotRequest<T> {
		private final Map<String, T> responses = new LinkedHashMap<>();
		private final Set<String> awaitingClients = new HashSet<>();
	}

	/**
	 * Registers a new request and records which connected clients are expected to answer it.
	 */
	private static <T> SnapshotRequest<T> beginSnapshotRequest(Set<SnapshotRequest<T>> requests) {
		SnapshotRequest<T> request = new SnapshotRequest<>();
		request.awaitingClients.addAll(clientChannels.values());
		requests.add(request);
		return request;
	}

	/**
	 * Hands a response to every request that is waiting for one and wakes up their waiters.
	 */
	private static <T> void deliverSnapshotResponse(Set<SnapshotRequest<T>> requests, String clientName, T response) {
		for (SnapshotRequest<T> request : requests) {
			synchronized (request) {
				request.responses.put(clientName, response);
				request.awaitingClients.remove(clientName);
				request.notifyAll();
			}
		}
	}

	/**
	 * Waits until every expected client has answered or the deadline has passed. A client that disconnects
	 * while we wait is dropped from the expected set by {@link #removeClientChannel(Channel)}, which wakes
	 * this waiter instead of leaving it to time out.
	 */
	private static <T> void awaitSnapshotResponse(SnapshotRequest<T> request, long deadlineMillis) {
		synchronized (request) {
			while (!request.awaitingClients.isEmpty()) {
				long remaining = deadlineMillis - System.currentTimeMillis();
				if (remaining <= 0) {
					break;
				}
				try {
					request.wait(remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	/**
	 * Unregisters a finished request and returns a private copy of the responses it collected.
	 */
	private static <T> Map<String, T> collectSnapshotResponse(Set<SnapshotRequest<T>> requests, SnapshotRequest<T> request) {
		requests.remove(request);
		synchronized (request) {
			return new LinkedHashMap<>(request.responses);
		}
	}

	private static <T> void forgetAwaitingClient(Set<SnapshotRequest<T>> requests, String clientName) {
		for (SnapshotRequest<T> request : requests) {
			synchronized (request) {
				if (request.awaitingClients.remove(clientName)) {
					request.notifyAll();
				}
			}
		}
	}

	private static <T> void dropAwaitingClients(Set<SnapshotRequest<T>> requests) {
		for (SnapshotRequest<T> request : requests) {
			synchronized (request) {
				if (!request.awaitingClients.isEmpty()) {
					request.awaitingClients.clear();
					request.notifyAll();
				}
			}
		}
	}

	// ===== Info Methods =====

	public static void cacheInfoResponse(String clientName, CommandPackets.Info.ResponsePacket packet) {
		if (packet == null) {
			return;
		}

		String name = resolveInfoResponseName(clientName, packet);

		if (packet.serverName == null || packet.serverName.isBlank()) {
			// Written on the thread that received or created the packet, before it is published to any waiter
			packet.serverName = name;
		}

		deliverSnapshotResponse(infoRequests, name, packet);
	}

	private static String resolveInfoResponseName(String clientName, CommandPackets.Info.ResponsePacket packet) {
		String name = clientName;
		if (name == null || name.isBlank()) {
			name = packet.serverName;
		}
		if (name == null || name.isBlank()) {
			name = "unknown";
		}
		return name;
	}

	public static Map<String, CommandPackets.Info.ResponsePacket> requestInfoSnapshot(int timeoutSeconds) {
		SnapshotRequest<CommandPackets.Info.ResponsePacket> request = beginSnapshotRequest(infoRequests);

		if (!request.awaitingClients.isEmpty()) {
			broadcastToClients(new CommandPackets.Info.RequestPacket(System.currentTimeMillis()));
		} else if (clientInstance.get() != null) {
			CommandPackets.Info.ResponsePacket localPacket = createResponsePacket();
			request.awaitingClients.add(resolveInfoResponseName(localPacket.serverName, localPacket));
			cacheInfoResponse(localPacket.serverName, localPacket);
		}

		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
		awaitSnapshotResponse(request, deadlineMillis);
		return collectSnapshotResponse(infoRequests, request);
	}

	public static CommandPackets.Info.ResponsePacket createResponsePacket() {
		Supplier<CommandPackets.Info.ResponsePacket> supplier = infoSupplier.get();
		CommandPackets.Info.ResponsePacket packet = supplier != null ? supplier.get() : null;

		if (packet == null) {
			Runtime runtime = Runtime.getRuntime();
			return new CommandPackets.Info.ResponsePacket(
					getClientServerName(),
					-1,
					defaultMinecraftVersion(),
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

		// The supplier's own instance is never filled in afterwards: a packet that needs defaults is replaced
		// by one that already carries every value, so no thread can observe a half-updated packet
		boolean serverNameMissing = packet.serverName == null || packet.serverName.isBlank();
		boolean minecraftVersionMissing = packet.minecraftVersion == null || packet.minecraftVersion.isBlank();
		if (!serverNameMissing && !minecraftVersionMissing) {
			return packet;
		}

		return new CommandPackets.Info.ResponsePacket(
				serverNameMissing ? getClientServerName() : packet.serverName,
				packet.connectionLatencyMillis,
				minecraftVersionMissing ? defaultMinecraftVersion() : packet.minecraftVersion,
				packet.onlinePlayerCount,
				packet.maxPlayerCount,
				packet.playersAndLatencies,
				packet.playersEverJoined,
				packet.tps,
				packet.mspt,
				packet.uptimeSeconds,
				packet.totalMemory,
				packet.freeMemory
		);
	}

	private static String defaultMinecraftVersion() {
		return EnvironmentUtils.isMinecraftEnvironment()
				? EnvironmentUtils.getMinecraftVersion()
				: "unknown";
	}

	// ===== DMCC Command Auto-Complete Methods =====

	public static void cacheExecuteAutoCompleteResponse(String clientName, List<String> suggestions) {
		deliverSnapshotResponse(executeAutoCompleteRequests, clientName, suggestions);
	}

	public static Map<String, List<String>> requestExecuteAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				executeAutoCompleteRequests,
				new CommandPackets.Execute.AutoCompleteRequestPacket(input, opLevel),
				timeoutSeconds
		);
	}

	// ===== Minecraft Command Auto-Complete Methods =====

	public static void cacheConsoleAutoCompleteResponse(String clientName, List<String> suggestions) {
		deliverSnapshotResponse(consoleAutoCompleteRequests, clientName, suggestions);
	}

	public static Map<String, List<String>> requestConsoleAutoCompleteSnapshot(String input, int opLevel, int timeoutSeconds) {
		return requestAutoCompleteSnapshot(
				consoleAutoCompleteRequests,
				new CommandPackets.Console.AutoCompleteRequestPacket(input, opLevel),
				timeoutSeconds
		);
	}

	private static Map<String, List<String>> requestAutoCompleteSnapshot(Set<SnapshotRequest<List<String>>> requests,
	                                                                    Packet requestPacket,
	                                                                    int timeoutSeconds) {
		SnapshotRequest<List<String>> request = beginSnapshotRequest(requests);

		if (!request.awaitingClients.isEmpty()) {
			broadcastToClients(requestPacket);
		}

		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
		awaitSnapshotResponse(request, deadlineMillis);
		return collectSnapshotResponse(requests, request);
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
