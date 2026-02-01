package com.xujiayao.discord_mc_chat.network;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.server.ServerDMCC;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Central access point for sending network packets.
 *
 * @author Xujiayao
 */
public class NetworkManager {

	private static final AtomicReference<ClientDMCC> clientInstance = new AtomicReference<>();
	private static final AtomicReference<ServerDMCC> serverInstance = new AtomicReference<>();

	public static void registerClient(ClientDMCC client) {
		clientInstance.set(client);
	}

	public static void registerServer(ServerDMCC server) {
		serverInstance.set(server);
	}

	public static void clear() {
		clientInstance.set(null);
		serverInstance.set(null);
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

//	/**
//	 * Sends a packet to a client. Should be called from server only.
//	 *
//	 * @param packet The packet to send
//	 * @param targetName The target client's name
//	 */
//	public static void sendPacketToClient(Packet packet, String targetName) {
//		ServerDMCC server = serverInstance.get();
//		if (server != null) {
//			server.sendPacket(packet);
//		}
//	}
//
//	/**
//	 * Sends a packet to all clients. Should be called from server only.
//	 *
//	 * @param packet The packet to send
//	 */
//	public static void sendPacketToAll(Packet packet) {
//		ServerDMCC server = serverInstance.get();
//		if (server != null) {
//			server.sendPacket(packet);
//		}
//	}
}
