package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all server-side components.
 *
 * @author Xujiayao
 */
public class ServerDMCC {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Server"));
	private final String host;
	private NettyServer nettyServer;
	private int port;
	private Future<Integer> startFuture;

	public ServerDMCC(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public ServerDMCC(String host) {
		this.host = host;
		this.port = 0;
	}

	public void start() {
		startFuture = executor.submit(() -> {
			LOGGER.info("Starting DMCC Server component...");

			if (!DiscordManager.init()) {
				LOGGER.error("Failed to initialize Discord Manager. Server component will not start.");
				return -1;
			}

			nettyServer = new NettyServer();
			port = nettyServer.start(host, port);
			return port;
		});
	}

	/**
	 * Waits for the server component to start and returns the bound port.
	 *
	 * @return The port the server is bound to, or -1 on failure.
	 * @throws Exception If the startup future was interrupted or failed.
	 */
	public int awaitStartedPort() throws Exception {
		if (startFuture == null) {
			throw new IllegalStateException("Server has not been started yet.");
		}
		return startFuture.get();
	}

	public void shutdown() {
		LOGGER.info("Shutting down DMCC Server component...");

		if (nettyServer != null) {
			nettyServer.stop();
		}

		try {
			DiscordManager.shutdown();
		} catch (InterruptedException e) {
			LOGGER.error("Discord manager shutdown was interrupted.", e);
			Thread.currentThread().interrupt();
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

		LOGGER.info("DMCC Server component shut down.");
	}
}
