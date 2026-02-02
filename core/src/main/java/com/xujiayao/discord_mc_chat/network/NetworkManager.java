package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central access point for sending network packets and managing connections.
 *
 * @author Xujiayao
 */
public class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();

	// Server-side: Manage connected client channels
	private static final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();

	/**
	 * Registers the client instance for network operations.
	 *
	 * @param client The client instance
	 */
	public static void registerClient(ClientDMCC client) {
		clientInstance.set(client);
	}

	/**
	 * Resets the network manager state, clearing client instance and channels.
	 */
	public static void clear() {
		clientInstance.set(null);
		clientChannels.clear();
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
}
