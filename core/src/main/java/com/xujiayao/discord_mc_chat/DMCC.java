package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.commands.CommandEventHandler;
import com.xujiayao.discord_mc_chat.server.ServerDMCC;
import com.xujiayao.discord_mc_chat.standalone.TerminalManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.logging.impl.LoggerImpl;
import okhttp3.Cache;

import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import static com.xujiayao.discord_mc_chat.Constants.OK_HTTP_CLIENT;
import static com.xujiayao.discord_mc_chat.Constants.VERSION;

/**
 * The main class of Discord-MC-Chat (DMCC).
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
			String versionString = VERSION + " ".repeat(Math.max(0, 34 - VERSION.length()));

			// Print the DMCC banner
			LOGGER.info("┌─────────────────────────────────────────────────────────────────────────────────┐");
			LOGGER.info("│  ____  _                       _       __  __  ____       ____ _           _    │");
			LOGGER.info("│ |  _ \\(_)___  ___ ___  _ __ __| |     |  \\/  |/ ___|     / ___| |__   __ _| |_  │");
			LOGGER.info("│ | | | | / __|/ __/ _ \\| '__/ _` |_____| |\\/| | |   _____| |   | '_ \\ / _` | __| │");
			LOGGER.info("│ | |_| | \\__ \\ (_| (_) | | | (_| |_____| |  | | |__|_____| |___| | | | (_| | |_  │");
			LOGGER.info("│ |____/|_|___/\\___\\___/|_|  \\__,_|     |_|  |_|\\____|     \\____|_| |_|\\__,_|\\__| │");
			LOGGER.info("│                                                                                 │");
			LOGGER.info("│ Discord-MC-Chat (DMCC) {} Discord-MC-Chat Docs: │", versionString);
			LOGGER.info("│ By Xujiayao                                          https://dmcc.xujiayao.com/ │");
			LOGGER.info("└─────────────────────────────────────────────────────────────────────────────────┘");

			LOGGER.info("Initializing DMCC {} with IS_MINECRAFT_ENV: {}", VERSION, IS_MINECRAFT_ENV);

			// Initialize Command event handlers
			CommandEventHandler.init();

			if (!IS_MINECRAFT_ENV) {
				// Initialize terminal manager for standalone mode
				TerminalManager.init();
			}

			// If configuration fails to load, exit the DMCC-Main thread gracefully
			// In a Minecraft environment, we just return and let the server continue running
			// In standalone mode, the process will also remain alive awaiting user to reload
			// User can run the reload command after fixing the issues

			String reloadCommand = IS_MINECRAFT_ENV ? "/dmcc reload" : "/reload";

			// Load DMCC internal translation
			if (!I18nManager.loadInternalTranslationsOnly()) {
				// Should not happen!
				LOGGER.warn("DMCC will not continue initialization due to internal language file issues");
				LOGGER.warn("This is a critical error, please report it to the developer!");
				return;
			}

			if (!ModeManager.load() // Determine operating mode
					|| !ConfigManager.load() // Load configuration
					|| !I18nManager.load(ConfigManager.getString("language"))) { // Load all translations
				LOGGER.warn("Please correct the errors mentioned above, then run \"{}\".", reloadCommand);
				return;
			}

			// From now on should separate ServerDMCC and ClientDMCC initialization based on mode
			switch (ModeManager.getMode()) {
				case "single_server" -> {
					try {
						serverInstance = new ServerDMCC("127.0.0.1"); // Bind to a random port
						serverInstance.start();

						int port = serverInstance.awaitStartedPort();
						if (port != -1) {
							clientInstance = new ClientDMCC("127.0.0.1", port);
							clientInstance.start();
						}
					} catch (Exception e) {
						LOGGER.error("Failed to start single_server mode", e);
					}
				}
				case "multi_server_client" -> {
					String host = ConfigManager.getString("multi_server.connection.host");
					int port = ConfigManager.getInt("multi_server.connection.port");
					clientInstance = new ClientDMCC(host, port);
					clientInstance.start();
				}
				case "standalone" -> {
					String host = ConfigManager.getString("multi_server.connection.host");
					int port = ConfigManager.getInt("multi_server.connection.port");
					serverInstance = new ServerDMCC(host, port);
					serverInstance.start();
				}
			}
		}, "DMCC-Main").start();

		// TODO should be a Future, only this if returned true
		LOGGER.info("DMCC initialized successfully!");
	}

	/**
	 * Shuts down DMCC.
	 */
	public static void shutdown() {
		if (clientInstance != null) {
			clientInstance.shutdown();
		}
		if (serverInstance != null) {
			serverInstance.shutdown();
		}

		if (!IS_MINECRAFT_ENV) {
			TerminalManager.shutdown();
		}

		// Shutdown Command event handler
		CommandEventHandler.shutdown();

		// Clear all event handlers
		EventManager.clear();

		// Shutdown OkHttpClient
		try (ExecutorService executor = OK_HTTP_CLIENT.dispatcher().executorService();
			 Cache ignored = OK_HTTP_CLIENT.cache()) {
			ExecutorServiceUtils.shutdownAnExecutor(executor);

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
