package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.packets.CommandAutoCompleteRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.InfoRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Central access point for sending network packets and managing connections.
 *
 * @author Xujiayao
 */
public class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();

	// Server-side: Manage connected client channels
	private static final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();

	private static final Map<String, InfoResponsePacket> infoCache = new ConcurrentHashMap<>();
	private static final AtomicReference<Supplier<InfoResponsePacket>> infoSupplier = new AtomicReference<>();
	private static final Object infoLock = new Object();

	// Auto-complete cache
	private static final Map<String, List<String>> autoCompleteCache = new ConcurrentHashMap<>();
	private static final Object autoCompleteLock = new Object();

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
	public static void registerInfoSupplier(Supplier<InfoResponsePacket> supplier) {
		infoSupplier.set(supplier);
	}

	/**
	 * Resets the network manager state, clearing client instance and channels.
	 */
	public static void clear() {
		clientInstance.set(null);
		clientChannels.clear();
		infoCache.clear();
		autoCompleteCache.clear();
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
		clientChannels.forEach((channel, name) -> channel.writeAndFlush(packet));
	}

	/**
	 * Adds a client channel to the managed list.
	 *
	 * @param channel The client channel
	 * @param name    The name of the client
	 */
	public static void addClientChannel(Channel channel, String name) {
		clientChannels.put(channel, name);
	}

	/**
	 * Removes a client channel from the managed list.
	 *
	 * @param channel The client channel
	 */
	public static void removeClientChannel(Channel channel) {
		clientChannels.remove(channel);
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
		return clientChannels.values().stream().anyMatch(name -> name.equals(clientName));
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

	/**
	 * Stores a received InfoResponsePacket into cache.
	 *
	 * @param clientName The client name
	 * @param packet     The packet to cache
	 */
	public static void cacheInfoResponse(String clientName, InfoResponsePacket packet) {
		if (packet == null) {
			return;
		}

		String name = clientName;
		if (name == null || name.isBlank()) {
			name = packet.serverName;
		}
		if (name == null || name.isBlank()) {
			name = "unknown";
		}

		if (packet.serverName == null || packet.serverName.isBlank()) {
			packet.serverName = name;
		}

		infoCache.put(name, packet);

		synchronized (infoLock) {
			infoLock.notifyAll();
		}
	}

	/**
	 * Sends InfoRequest packets, blocks the current thread, then returns a snapshot of cached responses.
	 *
	 * @param timeoutSeconds The waiting time in seconds
	 * @return A snapshot of cached InfoResponsePacket items
	 */
	public static Map<String, InfoResponsePacket> requestInfoSnapshot(int timeoutSeconds) {
		infoCache.clear();

		int expectedResponses = clientChannels.size();

		if (expectedResponses > 0) {
			broadcastToClients(new InfoRequestPacket(System.currentTimeMillis()));
		}

		boolean includeLocalPacket = expectedResponses == 0 && clientInstance.get() != null;
		if (includeLocalPacket) {
			InfoResponsePacket localPacket = createInfoResponsePacket();
			cacheInfoResponse(localPacket.serverName, localPacket);
			expectedResponses += 1;
		}

		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

		if (expectedResponses > 0) {
			synchronized (infoLock) {
				while (infoCache.size() < expectedResponses) {
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

		Map<String, InfoResponsePacket> snapshot = new LinkedHashMap<>(infoCache);
		infoCache.clear();
		return snapshot;
	}

	/**
	 * Creates an InfoResponsePacket using the registered supplier or a fallback.
	 *
	 * @return The InfoResponsePacket instance
	 */
	public static InfoResponsePacket createInfoResponsePacket() {
		Supplier<InfoResponsePacket> supplier = infoSupplier.get();
		InfoResponsePacket packet = supplier != null ? supplier.get() : null;

		if (packet == null) {
			Runtime runtime = Runtime.getRuntime();
			String serverName = getClientServerName();
			String minecraftVersion = EnvironmentUtils.isMinecraftEnvironment()
					? EnvironmentUtils.getMinecraftVersion()
					: "unknown";

			packet = new InfoResponsePacket(
					serverName,
					-1,
					minecraftVersion,
					0,
					0,
					Map.of(),
					0,
					0,
					0,
					runtime.totalMemory(),
					runtime.freeMemory()
			);
		}

		if (packet.serverName == null || packet.serverName.isBlank()) {
			packet.serverName = getClientServerName();
		}

		if (packet.minecraftVersion == null || packet.minecraftVersion.isBlank()) {
			packet.minecraftVersion = EnvironmentUtils.isMinecraftEnvironment()
					? EnvironmentUtils.getMinecraftVersion()
					: "unknown";
		}

		return packet;
	}

	// ===== Auto-Complete Methods =====

	/**
	 * Caches an auto-complete response from a client.
	 *
	 * @param clientName  The client name
	 * @param suggestions The list of suggestions
	 */
	public static void cacheAutoCompleteResponse(String clientName, List<String> suggestions) {
		autoCompleteCache.put(clientName, suggestions);
		synchronized (autoCompleteLock) {
			autoCompleteLock.notifyAll();
		}
	}

	/**
	 * Requests auto-complete suggestions from all connected clients for the given input.
	 *
	 * @param input          The current user input to complete
	 * @param timeoutSeconds The waiting time in seconds
	 * @return A map of client name to suggestion list
	 */
	public static Map<String, List<String>> requestAutoCompleteSnapshot(String input, int timeoutSeconds) {
		autoCompleteCache.clear();

		int expectedResponses = clientChannels.size();
		if (expectedResponses > 0) {
			broadcastToClients(new CommandAutoCompleteRequestPacket(input));
		}

		long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

		if (expectedResponses > 0) {
			synchronized (autoCompleteLock) {
				while (autoCompleteCache.size() < expectedResponses) {
					long remaining = deadlineMillis - System.currentTimeMillis();
					if (remaining <= 0) break;
					try {
						autoCompleteLock.wait(remaining);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		Map<String, List<String>> snapshot = new LinkedHashMap<>(autoCompleteCache);
		autoCompleteCache.clear();
		return snapshot;
	}

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
