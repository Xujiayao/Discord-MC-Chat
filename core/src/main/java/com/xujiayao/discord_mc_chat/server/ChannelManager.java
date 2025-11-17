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
	 * This should only be called after successful authentication.
	 *
	 * @param serverName The unique name of the client server.
	 * @param channel    The channel to register.
	 */
	public static void registerChannel(String serverName, Channel channel) {
		ALL_CHANNELS.add(channel);
		NAME_TO_CHANNEL_MAP.put(serverName, channel);
		CHANNEL_TO_NAME_MAP.put(channel, serverName);

		LOGGER.info("Client \"{}\" registered from {}", serverName, channel.remoteAddress());
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
	 * Checks if a server name is already registered.
	 *
	 * @param serverName The server name to check.
	 * @return true if the name is already in use, false otherwise.
	 */
	public static boolean isNameRegistered(String serverName) {
		return NAME_TO_CHANNEL_MAP.containsKey(serverName);
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
