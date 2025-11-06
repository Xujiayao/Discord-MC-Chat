package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
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
	private NettyClient nettyClient;
	public static String serverName;

	public void start(String host, int port) {
		executor.submit(() -> {
			LOGGER.info("Starting DMCC Client component...");

			MinecraftEventHandler.init();

			serverName = ConfigManager.getString("multi_server.server_name", "Minecraft");

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
