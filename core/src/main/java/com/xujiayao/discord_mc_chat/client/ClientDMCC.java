package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all client-side components.
 *
 * @author Xujiayao
 */
public final class ClientDMCC {

	private final String host;
	private final int port;
	private final String serverName;
	private final String sharedSecret;
	private NettyClient nettyClient;

	public ClientDMCC(String host, int port, String serverName, String sharedSecret) {
		this.host = host;
		this.port = port;
		this.serverName = serverName;
		this.sharedSecret = sharedSecret;
	}

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

	public void shutdown() {
		ConsoleLogTailer.stop();
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

	public long getConnectionLatencyMillis() {
		return nettyClient == null ? 0 : nettyClient.getConnectionLatencyMillis();
	}

	public long requestLatencySample(long timeoutMillis) {
		return nettyClient == null ? -1 : nettyClient.requestLatencySample(timeoutMillis);
	}
}
