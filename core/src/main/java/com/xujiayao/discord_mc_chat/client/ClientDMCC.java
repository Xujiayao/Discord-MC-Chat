package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

public final class ClientDMCC {
	private static final AtomicBoolean PRESERVE_LOG_TAILER_ON_NEXT_SHUTDOWN = new AtomicBoolean(false);

	private final String host;
	private final int port;
	private final String serverName;
	private final String sharedSecret;
	private NettyClient nettyClient;

	/**
	 * @param serverName Logical client/server name used in DMCC protocol.
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
		try (ExecutorService executor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-Client"))) {
			return executor.submit(() -> {
				nettyClient = new NettyClient(host, port, serverName, sharedSecret);
				boolean success = nettyClient.start();
				if (success) {
					NetworkManager.registerClient(this);
				}
				return success;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("client.startup_interrupted"), e);
			return false;
		}
	}

	public void shutdown() {
		boolean preserveLogTailerState = PRESERVE_LOG_TAILER_ON_NEXT_SHUTDOWN.getAndSet(false);
		if (!preserveLogTailerState) {
			ConsoleLogTailer.stop();
		}
		if (nettyClient != null) {
			nettyClient.stop();
		}
	}

	public void sendPacket(Packet packet) {
		if (nettyClient != null) {
			nettyClient.sendPacket(packet);
		}
	}

	public String getServerName() {
		return serverName;
	}

	public boolean isConnected() {
		return nettyClient != null && nettyClient.isConnected();
	}

	/**
	 * @return Connection latency in milliseconds, or {@code 0} when unavailable.
	 */
	public long getConnectionLatencyMillis() {
		return nettyClient == null ? 0 : nettyClient.getConnectionLatencyMillis();
	}

	/**
	 * @param timeoutMillis Timeout in milliseconds for waiting a sample.
	 * @return Sampled latency in milliseconds, or {@code -1} when unavailable/timed out.
	 */
	public long requestLatencySample(long timeoutMillis) {
		return nettyClient == null ? -1 : nettyClient.requestLatencySample(timeoutMillis);
	}
}
