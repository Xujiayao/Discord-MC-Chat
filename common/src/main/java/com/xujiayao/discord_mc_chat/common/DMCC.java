package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.utils.Utils;
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
	 * NeoForge and Standalone use this method.
	 *
	 * @param loader The name of the loader
	 */
	public static void init(String loader) {
		init(loader, Utils.getVersionByResource());
	}

	/**
	 * Initialize DMCC with specified DMCC version.
	 * <p>
	 * Fabric uses this method.
	 *
	 * @param loader  The name of the loader
	 * @param version The version of DMCC
	 */
	public static void init(String loader, String version) {
		new Thread(() -> {
			VERSION = version;

			LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);
		}, "DMCC-Main").start();
	}
}
