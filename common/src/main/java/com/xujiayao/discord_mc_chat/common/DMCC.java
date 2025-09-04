package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.utils.Utils;
import com.xujiayao.discord_mc_chat.common.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * The main class of Discord MC Chat (DMCC).
 *
 * @author Xujiayao
 */
public class DMCC {

	public static final Logger LOGGER = new Logger();
	public static final boolean IS_MINECRAFT_ENV = Utils.isMinecraftEnvironment();
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
		init(loader, Utils.getVersionByResource());
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

			LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);

			if (ConfigManager.initialize()) {
				LOGGER.info("Configuration loaded successfully!");
			} else {
				LOGGER.warn("DMCC will not continue initialization due to configuration issues");
				LOGGER.info("Exiting...");

				// Exit the DMCC-Main thread gracefully
				// In a Minecraft environment, we just return and let the server continue running
				// In standalone mode, the process would terminate here
				return;
			}

			configExamples();
		}, "DMCC-Main").start();
	}

	private static void configExamples() {

	}
}
