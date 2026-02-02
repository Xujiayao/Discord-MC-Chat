package com.xujiayao.discord_mc_chat.server;

import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the lifecycle of all server-side components.
 *
 * @author Xujiayao
 */
public class ServerDMCC {

	private final String host;
	private final String sharedSecret;
	private NettyServer nettyServer;
	private int port;

	public ServerDMCC(String host, int port, String sharedSecret) {
		this.host = host;
		this.port = port;
		this.sharedSecret = sharedSecret;
	}

	/**
	 * Starts the DMCC server. Blocks until startup is complete.
	 *
	 * @return the port number the server is listening on, or -1 if startup failed
	 */
	public int start() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Server"))) {
			return executor.submit(() -> {
				if (!DiscordManager.init()) {
					LOGGER.error(I18nManager.getDmccTranslation("server.discord_init_failed"));
					return -1;
				}

				nettyServer = new NettyServer(host, port, sharedSecret);
				port = nettyServer.start();

				return port;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("server.startup_interrupted"), e);
			return -1;
		}
	}

	public void shutdown() {
		if (nettyServer != null) {
			nettyServer.stop();
		}

		DiscordManager.shutdown();
	}
}
