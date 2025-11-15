package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.network.Packet;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages all connected client channels.
 * This class is thread-safe.
 *
 * @author Xujiayao
 */
public class ChannelManager {

	private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private static final Map<String, Channel> NAME_TO_CHANNEL_MAP = new ConcurrentHashMap<>();
	private static final Map<Channel, String> CHANNEL_TO_NAME_MAP = new ConcurrentHashMap<>();

	/**
	 * Registers a new channel with its server name.
	 * Rejects the connection if the server name is already in use.
	 *
	 * @param serverName The unique name of the client server.
	 * @param channel    The channel to register.
	 * @return true if registration was successful, false otherwise.
	 */
	public static boolean registerChannel(String serverName, Channel channel) {
		if (NAME_TO_CHANNEL_MAP.containsKey(serverName)) {
			LOGGER.warn("Rejecting connection from {} with duplicate server name: {}", channel.remoteAddress(), serverName);
			return false;
		}

		ALL_CHANNELS.add(channel);
		NAME_TO_CHANNEL_MAP.put(serverName, channel);
		CHANNEL_TO_NAME_MAP.put(channel, serverName);

		LOGGER.info("Client \"{}\" registered from {}", serverName, channel.remoteAddress());
		return true;
	}

	/**
	 * Unregisters a channel when it becomes inactive.
	 *
	 * @param channel The channel to unregister.
	 */
	public static void unregisterChannel(Channel channel) {
		String serverName = CHANNEL_TO_NAME_MAP.remove(channel);
		if (serverName != null) {
			NAME_TO_CHANNEL_MAP.remove(serverName);
			LOGGER.info("Client \"{}\" from {} unregistered", serverName, channel.remoteAddress());
		}
		// The channel is automatically removed from the ChannelGroup when closed.
	}

	/**
	 * Sends a packet to a specific client server.
	 *
	 * @param serverName The name of the target server.
	 * @param packet     The packet to send.
	 */
	public static void sendTo(String serverName, Packet packet) {
		Channel channel = NAME_TO_CHANNEL_MAP.get(serverName);
		if (channel != null && channel.isActive()) {
			channel.writeAndFlush(packet);
		}
	}

	/**
	 * Broadcasts a packet to all connected client servers.
	 *
	 * @param packet The packet to broadcast.
	 */
	public static void broadcast(Packet packet) {
		ALL_CHANNELS.writeAndFlush(packet);
	}
}
