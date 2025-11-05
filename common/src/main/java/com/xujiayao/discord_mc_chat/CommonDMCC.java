package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.commands.CommandEventHandler;
import com.xujiayao.discord_mc_chat.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEventHandler;
import com.xujiayao.discord_mc_chat.standalone.TerminalManager;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.logging.impl.LoggerImpl;
import okhttp3.Cache;

import java.util.concurrent.ExecutorService;
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
public class CommonDMCC {

	private static boolean shutdownHookAdded = false;

	/**
	 * Initialize DMCC.
	 */
	public static void init() {
		new Thread(() -> {
			// Check if running in headless mode
			if (System.console() == null) {
				// The user likely started the application by double-clicking the JAR file
				// Generates a warning to remind the user to start DMCC from the command line
				LOGGER.warn("No console detected, indicating DMCC is running in headless mode");
				LOGGER.warn("DMCC does not support being started by double-clicking the JAR file");
				LOGGER.warn("Please start DMCC from the command line \"java -jar Discord-MC-Chat-{}.jar\"", VERSION);
				LOGGER.info("Exiting...");

				if (!IS_MINECRAFT_ENV) {
					System.exit(0);
				}
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

			// Load configuration
			if (!ConfigManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to configuration issues");
				LOGGER.info("Exiting...");

				if (!IS_MINECRAFT_ENV) {
					System.exit(0);
				}
				return;
			}

			// Load language files
			if (!I18nManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to language file issues");
				LOGGER.info("Exiting...");

				if (!IS_MINECRAFT_ENV) {
					System.exit(0);
				}
				return;
			}

			// Initialize Command event handlers
			CommandEventHandler.init();

			// Check environment
			if (IS_MINECRAFT_ENV) {
				// Initialize Minecraft event handlers
				LOGGER.info("Minecraft environment detected. Initializing Minecraft event handlers...");
				MinecraftEventHandler.init();
			} else {
				LOGGER.info("No Minecraft environment detected. DMCC will run in standalone mode.");

				if (!shutdownHookAdded) {
					// Register shutdown hook for standalone mode
					Runtime.getRuntime().addShutdownHook(new Thread(CommonDMCC::shutdown, "DMCC-Shutdown"));
					shutdownHookAdded = true;
				}

				// Initialize interactive terminal for standalone mode
				TerminalManager.init();
			}

			// Initialize Discord
			if (!DiscordManager.init()) {
				LOGGER.warn("DMCC will not continue initialization due to Discord connection issues");
				LOGGER.info("Exiting...");

				if (!IS_MINECRAFT_ENV) {
					System.exit(0);
				}
				return;
			}
		}, "DMCC-Main").start();
	}

	/**
	 * Shuts down DMCC.
	 */
	public static void shutdown() {
		LOGGER.info("Shutting down DMCC...");

		try (ExecutorService executorService = OK_HTTP_CLIENT.dispatcher().executorService();
			 Cache cache = OK_HTTP_CLIENT.cache()) {
			// Shutdown TerminalManager if in standalone mode
			if (!IS_MINECRAFT_ENV) {
				TerminalManager.shutdown();
			}

			// Shutdown Discord
			DiscordManager.shutdown();

			// Clear all event handlers
			EventManager.clear();

			// Shutdown OkHttpClient
			executorService.shutdown();
			if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
				executorService.shutdownNow(); // Force shutdown if not terminated gracefully
			}
			OK_HTTP_CLIENT.connectionPool().evictAll();
			if (cache != null) {
				cache.close();
			}

			// End of whole process
			LOGGER.info("DMCC shutdown successfully. Goodbye!");
		} catch (Exception e) {
			LOGGER.error("An error occurred during DMCC shutdown", e);
		} finally {
			// Close the file logger
			LoggerImpl.closeFileWriter();
		}
	}

	/**
	 * Reloads DMCC by shutting it down and re-initializing.
	 */
	public static void reload() {
		shutdown();
		init();
		LOGGER.info("DMCC reloaded!");
	}
}
