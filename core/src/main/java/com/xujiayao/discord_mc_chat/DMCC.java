package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.commands.CommandEventHandler;
import com.xujiayao.discord_mc_chat.server.ServerDMCC;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.logging.impl.LoggerImpl;
import okhttp3.Cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.OK_HTTP_CLIENT;
import static com.xujiayao.discord_mc_chat.Constants.VERSION;

/**
 * The main class of Discord MC Chat (DMCC).
 *
 * @author Xujiayao
 */
public class DMCC {

	private static ServerDMCC serverInstance;
	private static ClientDMCC clientInstance;

	/**
	 * Initialize DMCC.
	 */
	public static void init() {
		new Thread(() -> {
			// Check if running in headless mode
			if (System.console() == null && !IS_MINECRAFT_ENV) {
				// The user likely started the application by double-clicking the JAR file in a GUI environment
				// Generates a warning to remind the user to start DMCC from the command line
				LOGGER.warn("No console detected, indicating DMCC is running in headless mode");
				LOGGER.warn("DMCC does not support being started by double-clicking the JAR file");
				LOGGER.warn("Please start DMCC from the command line \"java -jar Discord-MC-Chat-{}.jar\"", VERSION);

				return;
			}

			// Pad the version string to ensure consistent formatting in the banner
			String versionString = VERSION + " ".repeat(Math.max(0, 31 - VERSION.length()));

			// Print the DMCC banner
			LOGGER.info("┌─────────────────────────────────────────────────────────────────────────────────┐");
			LOGGER.info("│  ____  _                       _       __  __  ____       ____ _           _    │");
			LOGGER.info("│ |  _ \\(_)___  ___ ___  _ __ __| |     |  \\/  |/ ___|     / ___| |__   __ _| |_  │");
			LOGGER.info("│ | | | | / __|/ __/ _ \\| '__/ _` |_____| |\\/| | |   _____| |   | '_ \\ / _` | __| │");
			LOGGER.info("│ | |_| | \\__ \\ (_| (_) | | | (_| |_____| |  | | |__|_____| |___| | | | (_| | |_  │");
			LOGGER.info("│ |____/|_|___/\\___\\___/|_|  \\__,_|     |_|  |_|\\____|     \\____|_| |_|\\__,_|\\__| │");
			LOGGER.info("│                                                                                 │");
			LOGGER.info("│ Discord-MC-Chat (DMCC) {} More Information + Docs: │", versionString);
			LOGGER.info("│ By Xujiayao                                          https://dmcc.xujiayao.com/ │");
			LOGGER.info("└─────────────────────────────────────────────────────────────────────────────────┘");

			LOGGER.info("Initializing DMCC {} with IS_MINECRAFT_ENV: {}", VERSION, IS_MINECRAFT_ENV);

			// If configuration fails to load, exit the DMCC-Main thread gracefully
			// In a Minecraft environment, we just return and let the server continue running
			// In standalone mode, the process would terminate after returning

			// Determine operating mode
			if (!ModeManager.load()) {
				LOGGER.warn("DMCC initialization halted because an operating mode needs to be selected");
				return;
			}

			// Load configuration
			if (!ConfigManager.load(ModeManager.getMode())) {
				LOGGER.warn("DMCC will not continue initialization due to configuration issues");
				return;
			}

			// Load language files
			if (!I18nManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to language file issues");
				return;
			}

			// Initialize Command event handlers
			CommandEventHandler.init();

			// From now on should separate ServerDMCC and ClientDMCC initialization based on mode
			switch (ModeManager.getMode()) {
				case "single_server" -> {
					try {
						LOGGER.info("Running in single_server mode. Starting internal server and client...");

						serverInstance = new ServerDMCC();
						Future<Integer> portFuture = serverInstance.start(0); // Bind to a random port
						int port = portFuture.get();

						if (port != -1) {
							clientInstance = new ClientDMCC();
							clientInstance.start("localhost", port);
						}
					} catch (Exception e) {
						LOGGER.error("Failed to start single_server mode", e);
					}
				}
				case "multi_server_client" -> {
					LOGGER.info("Running in multi_server_client mode. Starting client only.");

					clientInstance = new ClientDMCC();
					String host = ConfigManager.getString("multi_server.connection.host");
					int port = ConfigManager.getInt("multi_server.connection.port");
					clientInstance.start(host, port);
				}
				case "standalone" -> {
					LOGGER.info("Running in standalone mode. Starting server only.");

					serverInstance = new ServerDMCC();
					int port = ConfigManager.getInt("multi_server.connection.port");
					serverInstance.start(port);
				}
			}
		}, "DMCC-Main").start();
	}

	/**
	 * Shuts down DMCC.
	 */
	public static void shutdown() {
		LOGGER.info("Shutting down DMCC...");

		if (clientInstance != null) {
			clientInstance.shutdown();
		}
		if (serverInstance != null) {
			serverInstance.shutdown();
		}

		// Shutdown Command event handler
		CommandEventHandler.shutdown();

		// Clear all event handlers
		EventManager.clear();

		// Shutdown OkHttpClient
		try (ExecutorService executorService = OK_HTTP_CLIENT.dispatcher().executorService();
			 Cache ignored = OK_HTTP_CLIENT.cache()) {
			executorService.shutdown();
			if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				executorService.shutdownNow(); // Force shutdown if not terminated gracefully
			}
			OK_HTTP_CLIENT.connectionPool().evictAll();
		} catch (Exception e) {
			LOGGER.error("An error occurred during OkHttpClient shutdown", e);
		}

		LOGGER.info("DMCC shutdown successfully. Goodbye!");

		// Close the file logger
		LoggerImpl.closeFileWriter();
	}

	/**
	 * Reloads DMCC by shutting it down and re-initializing.
	 */
	public static void reload() {
		new Thread(() -> {
			shutdown();
			init();
			LOGGER.info("DMCC reloaded!");
		}, "DMCC-Reload").start();
	}
}
