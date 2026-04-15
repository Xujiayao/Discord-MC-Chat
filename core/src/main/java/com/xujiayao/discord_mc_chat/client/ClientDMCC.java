package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all client-side components.
 *
 * @author Xujiayao
 */
public final class ClientDMCC {
	private static final AtomicBoolean PRESERVE_LOG_TAILER_ON_NEXT_SHUTDOWN = new AtomicBoolean(false);

	private final String host;
	private final int port;
	private final String serverName;
	private final String sharedSecret;
	private NettyClient nettyClient;

	/**
	 * Creates a DMCC client wrapper.
	 *
	 * @param host         Target DMCC server host.
	 * @param port         Target DMCC server port.
	 * @param serverName   Logical client/server name used in DMCC protocol.
	 * @param sharedSecret Shared secret used for authentication handshake.
	 */
	public ClientDMCC(String host, int port, String serverName, String sharedSecret) {
		this.host = host;
		this.port = port;
		this.serverName = serverName;
		this.sharedSecret = sharedSecret;
	}

	/**
	 * Requests keeping the log tailer alive for the next client shutdown.
	 */
	public static void preserveLogTailerOnNextShutdown() {
		PRESERVE_LOG_TAILER_ON_NEXT_SHUTDOWN.set(true);
	}

	/**
	 * Starts the network client and performs initial login.
	 *
	 * @return {@code true} if startup and login succeed; {@code false} otherwise.
	 */
	public boolean start() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Client"))) {
			return executor.submit(() -> {
				nettyClient = new NettyClient(host, port, serverName, sharedSecret);
				boolean success = nettyClient.start();
				if (success) {
					// Register to NetworkManager on successful start
					NetworkManager.registerClient(this);
				}
				return success;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("client.startup_interrupted"), e);
			return false;
		}
	}

	/**
	 * Stops client networking and console log tailing.
	 */
	public void shutdown() {
		boolean preserveLogTailerState = PRESERVE_LOG_TAILER_ON_NEXT_SHUTDOWN.getAndSet(false);
		if (!preserveLogTailerState) {
			ConsoleLogTailer.stop();
		}
		if (nettyClient != null) {
			nettyClient.stop();
		}
	}

	/**
	 * Sends a packet to the connected DMCC server.
	 *
	 * @param packet Packet to send.
	 */
	public void sendPacket(Packet packet) {
		if (nettyClient != null) {
			nettyClient.sendPacket(packet);
		}
	}

	/**
	 * Gets the logical server name configured for this client.
	 *
	 * @return Configured logical server name.
	 */
	public String getServerName() {
		return serverName;
	}

	/**
	 * Indicates whether network channel is currently connected.
	 *
	 * @return {@code true} when connected; otherwise {@code false}.
	 */
	public boolean isConnected() {
		return nettyClient != null && nettyClient.isConnected();
	}

	/**
	 * Gets the latest measured connection latency.
	 *
	 * @return Connection latency in milliseconds, or {@code 0} when unavailable.
	 */
	public long getConnectionLatencyMillis() {
		return nettyClient == null ? 0 : nettyClient.getConnectionLatencyMillis();
	}

	/**
	 * Requests an active latency sample from server.
	 *
	 * @param timeoutMillis Timeout in milliseconds for waiting a sample.
	 * @return Sampled latency in milliseconds, or {@code -1} when unavailable/timed out.
	 */
	public long requestLatencySample(long timeoutMillis) {
		return nettyClient == null ? -1 : nettyClient.requestLatencySample(timeoutMillis);
	}
}
