package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
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
public final class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();

	// Server-side: Manage connected client channels
	private static final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();
	private static final Map<String, Long> clientConnectedAt = new ConcurrentHashMap<>();

	private static final Map<String, CommandPackets.Info.ResponsePacket> infoCache = new ConcurrentHashMap<>();
	private static final AtomicReference<Supplier<CommandPackets.Info.ResponsePacket>> infoSupplier = new AtomicReference<>();
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
	public static void registerInfoSupplier(Supplier<CommandPackets.Info.ResponsePacket> supplier) {
		infoSupplier.set(supplier);
	}

	/**
	 * Resets the network manager state, clearing client instance and channels.
	 */
	public static void clear() {
		clientInstance.set(null);
		clientChannels.clear();
		clientConnectedAt.clear();
		infoCache.clear();
		executeAutoCompleteCache.clear();
		consoleAutoCompleteCache.clear();
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
	 * Stores a received ResponsePacket into cache.
	 *
	 * @param clientName The client name
	 * @param packet     The packet to cache
	 */
	public static void cacheInfoResponse(String clientName, CommandPackets.Info.ResponsePacket packet) {
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
	 * @return A snapshot of cached ResponsePacket items
	 */
	public static Map<String, CommandPackets.Info.ResponsePacket> requestInfoSnapshot(int timeoutSeconds) {
		infoCache.clear();

		int expectedResponses = clientChannels.size();

		if (expectedResponses > 0) {
			broadcastToClients(new CommandPackets.Info.RequestPacket(System.currentTimeMillis()));
		}

		boolean includeLocalPacket = expectedResponses == 0 && clientInstance.get() != null;
		if (includeLocalPacket) {
			CommandPackets.Info.ResponsePacket localPacket = createResponsePacket();
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

		Map<String, CommandPackets.Info.ResponsePacket> snapshot = new LinkedHashMap<>(infoCache);
		infoCache.clear();
		return snapshot;
	}

	/**
	 * Creates an ResponsePacket using the registered supplier or a fallback.
	 *
	 * @return The ResponsePacket instance
	 */
	public static CommandPackets.Info.ResponsePacket createResponsePacket() {
		Supplier<CommandPackets.Info.ResponsePacket> supplier = infoSupplier.get();
		CommandPackets.Info.ResponsePacket packet = supplier != null ? supplier.get() : null;

		if (packet == null) {
			Runtime runtime = Runtime.getRuntime();
			String serverName = getClientServerName();
			String minecraftVersion = EnvironmentUtils.isMinecraftEnvironment()
					? EnvironmentUtils.getMinecraftVersion()
					: "unknown";

			packet = new CommandPackets.Info.ResponsePacket(
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
				new CommandPackets.Execute.AutoCompleteRequestPacket(input, opLevel),
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
				new CommandPackets.Console.AutoCompleteRequestPacket(input, opLevel),
				timeoutSeconds,
				false
		);
	}

	/**
	 * Shared implementation for requesting auto-complete suggestions from all connected clients.
	 * Broadcasts the request packet, waits for all clients to respond (or timeout), then returns a snapshot.
	 *
	 * @param cache          The cache map to populate with responses
	 * @param requestPacket  The packet to broadcast to clients
	 * @param timeoutSeconds The maximum wait time in seconds
	 * @param executeRequest Whether this request is for execute-command auto-complete
	 * @return A snapshot of the collected suggestions, keyed by client name
	 */
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
