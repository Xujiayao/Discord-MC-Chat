package com.xujiayao.discord_mc_chat.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all client-side components.
 *
 * @author Xujiayao
 */
public class ClientDMCC {

	private final String host;
	private final int port;
	private NettyClient nettyClient;

	public ClientDMCC(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public boolean start() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Server"))) {
			return executor.submit(() -> {
				nettyClient = new NettyClient(host, port);
				return nettyClient.start();
			}).get();
		} catch (Exception e) {
			LOGGER.error("DMCC-Client startup was interrupted", e);
			return false;
		}
	}

	public void shutdown() {
		if (nettyClient != null) {
			nettyClient.stop();
		}
	}
}
