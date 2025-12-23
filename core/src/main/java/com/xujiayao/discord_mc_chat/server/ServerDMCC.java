package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
			if (!DiscordManager.init()) {
				LOGGER.error("Failed to initialize Discord Manager. Server component will not start.");
				return -1;
			}

			nettyServer = new NettyServer(host, port);
			port = nettyServer.start();

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
		if (nettyServer != null) {
			nettyServer.stop();
		}

		DiscordManager.shutdown();

		ExecutorServiceUtils.shutdownAnExecutor(executor);
	}
}
