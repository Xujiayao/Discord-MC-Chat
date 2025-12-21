package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all client-side components.
 *
 * @author Xujiayao
 */
public class ClientDMCC {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Client"));
	private final String host;
	private final int port;
	private NettyClient nettyClient;

	public ClientDMCC(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() {
		executor.submit(() -> {
			LOGGER.info("Starting DMCC Client component...");

			nettyClient = new NettyClient(host, port);
			nettyClient.start();

			LOGGER.info("DMCC Client component started successfully.");
		});
	}

	public void shutdown() {
		LOGGER.info("Shutting down DMCC Client component...");

		if (nettyClient != null) {
			nettyClient.stop();
		}

		ExecutorServiceUtils.shutdownAnExecutor(executor);

		LOGGER.info("DMCC Client component shut down.");
	}
}
