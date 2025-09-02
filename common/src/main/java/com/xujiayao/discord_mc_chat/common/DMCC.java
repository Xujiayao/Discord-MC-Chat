package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.utils.Utils;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * @author Xujiayao
 */
public class DMCC {

	public static String VERSION;
	public static final Logger LOGGER = new Logger();

	public static void main(String[] args) {
		init("Standalone");
	}

	public static void init(String loader) {
		init(loader, Utils.getVersionByResource());
	}

	public static void init(String loader, String version) {
		new Thread(() -> {
			VERSION = version;

			LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);
		}, "DMCC-Main").start();
	}
}
