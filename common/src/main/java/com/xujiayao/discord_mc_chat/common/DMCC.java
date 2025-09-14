package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEventHandler;
import com.xujiayao.discord_mc_chat.common.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.common.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * The main class of Discord MC Chat (DMCC).
 *
 * @author Xujiayao
 */
public class DMCC {

	public static final Logger LOGGER = new Logger();
	public static String VERSION;

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

			LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);

			// If configuration fails to load, exit the DMCC-Main thread gracefully
			// In a Minecraft environment, we just return and let the server continue running
			// In standalone mode, the process would terminate after returning

			// Load configuration
			if (!ConfigManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to configuration issues");
				LOGGER.info("Exiting...");

				return;
			}

			// Load language files
			if (!I18nManager.load()) {
				LOGGER.warn("DMCC will not continue initialization due to language file issues");
				LOGGER.info("Exiting...");

				return;
			}

			// Initialize Minecraft event handlers
			MinecraftEventHandler.init();
		}, "DMCC-Main").start();
	}
}
