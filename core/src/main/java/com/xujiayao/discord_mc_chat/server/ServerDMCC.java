package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.standalone.TerminalManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all server-side components.
 *
 * @author Xujiayao
 */
public class ServerDMCC {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Server"));
	private NettyServer nettyServer;

	private final String host;
	private int port;

	public ServerDMCC(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public ServerDMCC(String host) {
		this.host = host;
		this.port = 0;
	}

	public Future<Integer> start() {
		return executor.submit(() -> {
			LOGGER.info("Starting DMCC Server component...");

			if (!DiscordManager.init()) {
				LOGGER.error("Failed to initialize Discord Manager. Server component will not start.");
				return -1;
			}

			if (!IS_MINECRAFT_ENV) {
				TerminalManager.init();
			}

			nettyServer = new NettyServer();
			this.port = nettyServer.start(host, port);
			return this.port;
		});
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

		if (!IS_MINECRAFT_ENV) {
			TerminalManager.shutdown();
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
