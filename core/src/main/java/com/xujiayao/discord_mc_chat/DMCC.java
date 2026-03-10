package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.client.ClientDMCC;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.server.ServerDMCC;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import okhttp3.Cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	 * Initialize DMCC. Blocks until initialization is complete.
	 *
	 * @return true if initialization is successful, false otherwise
	 */
	public static boolean init() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Init"))) {
			return executor.submit(() -> {
				// Load DMCC internal translation
				if (!I18nManager.loadInternalTranslationsOnly()) {
					// Should not happen!
					// No need to translate, as translations failed to load
					LOGGER.warn("DMCC will not continue initialization due to internal language file issues.");
					LOGGER.warn("This is a critical error, please report it to the developer!");
					return false;
				}

				// Check if running in headless mode
				if (System.console() == null && !IS_MINECRAFT_ENV) {
					// The user likely started the application by double-clicking the JAR file in a GUI environment
					// Generates a warning to remind the user to start DMCC from the command line
					LOGGER.warn(I18nManager.getDmccTranslation("main.init.headless.detected"));
					LOGGER.warn(I18nManager.getDmccTranslation("main.init.headless.not_supported"));
					LOGGER.warn(I18nManager.getDmccTranslation("main.init.headless.usage", VERSION));

					return false;
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

				if (IS_MINECRAFT_ENV) {
					LOGGER.info(I18nManager.getDmccTranslation("main.init.info_minecraft_env"));
				} else {
					LOGGER.info(I18nManager.getDmccTranslation("main.init.info_standalone_env"));
				}

				// If configuration fails to load, exit the DMCC-Init thread gracefully
				// In a Minecraft environment, we just return and let the server continue running
				// User can run the reload command after fixing the issues
				// In standalone mode, the process will exit, user can restart DMCC after fixing the issues

				boolean configs = !ModeManager.load() // Determine operating mode
						|| !ConfigManager.load() // Load configuration
						|| !I18nManager.load(ConfigManager.getString("language", I18nManager.detectLanguage())); // Load all translations

				// Initialize command system after internal translations and operating mode are loaded
				CommandManager.initialize();

				if (configs) {
					if (IS_MINECRAFT_ENV) {
						LOGGER.warn(I18nManager.getDmccTranslation("main.init.minecraft_reload_prompt"));
					} else {
						LOGGER.warn(I18nManager.getDmccTranslation("main.init.standalone_reload_prompt"));
					}
					return false;
				}

				// From now on should separate ServerDMCC and ClientDMCC initialization based on mode
				switch (ModeManager.getMode()) {
					case "single_server" -> {
						// Generate ephemeral credentials for internal loopback connection
						String internalServerName = "Internal";
						String internalSharedSecret = CryptUtils.generateRandomString(32);

						// Server instance gets the secret to verify the client
						serverInstance = new ServerDMCC("127.0.0.1", 0, internalSharedSecret);
						int port = serverInstance.start();

						if (port == -1) {
							LOGGER.error(I18nManager.getDmccTranslation("main.init.failed"));
							return false;
						} else {
							// Client instance gets the same secret to authenticate
							clientInstance = new ClientDMCC("127.0.0.1", port, internalServerName, internalSharedSecret);
							if (!clientInstance.start()) {
								LOGGER.error(I18nManager.getDmccTranslation("main.init.failed"));
								return false;
							}
						}
					}
					case "multi_server_client" -> {
						String host = ConfigManager.getString("multi_server.connection.host");
						int port = ConfigManager.getInt("multi_server.connection.port");
						String name = ConfigManager.getString("multi_server.server_name");
						String secret = ConfigManager.getString("multi_server.connection.shared_secret");

						clientInstance = new ClientDMCC(host, port, name, secret);
						if (!clientInstance.start()) {
							LOGGER.error(I18nManager.getDmccTranslation("main.init.failed"));
							return false;
						}
					}
					case "standalone" -> {
						String host = ConfigManager.getString("multi_server.connection.host");
						int port = ConfigManager.getInt("multi_server.connection.port");
						String secret = ConfigManager.getString("multi_server.connection.shared_secret");

						serverInstance = new ServerDMCC(host, port, secret);
						if (serverInstance.start() == -1) {
							LOGGER.error(I18nManager.getDmccTranslation("main.init.failed"));
							return false;
						}
					}
				}

				LOGGER.info(I18nManager.getDmccTranslation("main.init.success"));
				return true;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("main.init.failed"), e);
			return false;
		}
	}

	/**
	 * Shuts down DMCC.
	 *
	 * @return true if shutdown is successful, false otherwise
	 */
	public static boolean shutdown() {
		try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Shutdown"))) {
			return executor.submit(() -> {
				// Clear the network manager references first
				NetworkManager.clear();

				if (clientInstance != null) {
					clientInstance.shutdown();
					clientInstance = null;
				}
				if (serverInstance != null) {
					serverInstance.shutdown();
					serverInstance = null;
				}

				CommandManager.shutdown();

				// Do NOT clear event handlers here. They are registered once during mod initialization
				// and should persist across DMCC reloads.
				// EventManager.clear();

				// Shutdown OkHttpClient
				try (Cache ignored = OK_HTTP_CLIENT.cache()) {
					// OK_HTTP_CLIENT is static final. We should NOT shut down its dispatcher executor
					// because it would prevent the client from being reused after a reload.
					// ExecutorServiceUtils.shutdownAnExecutor(ok_http_executor);

					OK_HTTP_CLIENT.connectionPool().evictAll();
				} catch (Exception e) {
					LOGGER.error(I18nManager.getDmccTranslation("main.shutdown.okhttp_failed"), e);
					return false;
				}

				LOGGER.info(I18nManager.getDmccTranslation("main.shutdown.success"));
				return true;
			}).get();
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("main.shutdown.failed"), e);
			return false;
		}
	}

	/**
	 * Reloads DMCC by shutting it down and re-initializing (if shutdown is successful).
	 *
	 * @return true if reload is successful, false otherwise
	 */
	public static boolean reload() {
		return shutdown() && init();
	}
}
