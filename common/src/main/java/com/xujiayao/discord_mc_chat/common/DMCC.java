package com.xujiayao.discord_mc_chat.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.xujiayao.discord_mc_chat.common.commands.CommandEventHandler;
import com.xujiayao.discord_mc_chat.common.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEventHandler;
import com.xujiayao.discord_mc_chat.common.standalone.TerminalManager;
import com.xujiayao.discord_mc_chat.common.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.common.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.common.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;
import com.xujiayao.discord_mc_chat.common.utils.logging.impl.LoggerImpl;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The main class of Discord MC Chat (DMCC).
 *
 * @author Xujiayao
 */
public class DMCC {

	public static final Logger LOGGER = new Logger();

	public static String VERSION;
	public static String LOADER;

	public static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory()
			.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
	public static final ObjectMapper JSON_MAPPER = new ObjectMapper();


	public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

	private static boolean shutdownHookAdded = false;

	/**
	 * Main method for standalone execution.
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		init("Standalone");
	}

	/**
	 * Initialize DMCC with DMCC version auto-detected.
	 * <p>
	 * This method is invoked by NeoForge and Standalone.
	 *
	 * @param loader The name of the loader
	 */
	public static void init(String loader) {
		init(loader, EnvironmentUtils.getVersionByResource());
	}

	/**
	 * Initialize DMCC with specified DMCC version.
	 * <p>
	 * This method is invoked by Fabric.
	 * <p>
	 * NeoForge and Standalone use {@link #init(String)},
	 * which redirects to this method.
	 *
	 * @param loader  The name of the loader
	 * @param version The version of DMCC
	 */
	public static void init(String loader, String version) {
		new Thread(() -> {
			VERSION = version;
			LOADER = loader;

			// Check if running in headless mode
			if (System.console() == null) {
				// The user likely started the application by double-clicking the JAR file
				// Generates a warning to remind the user to start DMCC from the command line
				LOGGER.warn("No console detected, indicating DMCC is running in headless mode");
				LOGGER.warn("DMCC does not support being started by double-clicking the JAR file");
				LOGGER.warn("Please start DMCC from the command line \"java -jar Discord-MC-Chat-{}.jar\"", VERSION);
				LOGGER.info("Exiting...");

				if (!EnvironmentUtils.isMinecraftEnvironment()) {
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

			LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, LOADER);

			// If configuration fails to load, exit the DMCC-Main thread gracefully
			// In a Minecraft environment, we just return and let the server continue running
			// In standalone mode, the process would terminate after returning

			// Load configuration
			if (!ConfigManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to configuration issues");
				LOGGER.info("Exiting...");

				if (!EnvironmentUtils.isMinecraftEnvironment()) {
					System.exit(0);
				}
				return;
			}

			// Load language files
			if (!I18nManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to language file issues");
				LOGGER.info("Exiting...");

				if (!EnvironmentUtils.isMinecraftEnvironment()) {
					System.exit(0);
				}
				return;
			}

			// Initialize Command event handlers
			CommandEventHandler.init();

			// Check environment
			if (EnvironmentUtils.isMinecraftEnvironment()) {
				// Initialize Minecraft event handlers
				LOGGER.info("Minecraft environment detected. Initializing Minecraft event handlers...");
				MinecraftEventHandler.init();
			} else {
				LOGGER.warn("No Minecraft environment detected. DMCC will run in standalone mode.");

				if (!shutdownHookAdded) {
					// Register shutdown hook for standalone mode
					Runtime.getRuntime().addShutdownHook(new Thread(DMCC::shutdown, "DMCC-Shutdown"));
					shutdownHookAdded = true;
				}

				// Initialize interactive terminal for standalone mode
				TerminalManager.init();
			}

			// Initialize Discord
			if (!DiscordManager.init()) {
				LOGGER.warn("DMCC will not continue initialization due to Discord connection issues");
				LOGGER.info("Exiting...");

				if (!EnvironmentUtils.isMinecraftEnvironment()) {
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

		try (ExecutorService executorService = HTTP_CLIENT.dispatcher().executorService();
			 Cache cache = HTTP_CLIENT.cache()) {
			// Shutdown TerminalManager if in standalone mode
			if (!EnvironmentUtils.isMinecraftEnvironment()) {
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
			HTTP_CLIENT.connectionPool().evictAll();
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
		LOGGER.info("Reloading DMCC...");
		shutdown();
		init(LOADER, VERSION);
	}
}
