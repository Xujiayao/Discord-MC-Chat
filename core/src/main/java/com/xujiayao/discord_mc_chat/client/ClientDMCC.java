package com.xujiayao.discord_mc_chat.client;

import java.util.ServiceLoader;
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

			// Use ServiceLoader to initialize Minecraft-specific components
			ServiceLoader<MinecraftService> services = ServiceLoader.load(MinecraftService.class);
			services.forEach(MinecraftService::initialize);

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

		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}

		LOGGER.info("DMCC Client component shut down.");
	}
}
