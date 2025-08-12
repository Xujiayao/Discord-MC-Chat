package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.utils.Logger;
import com.xujiayao.discord_mc_chat.common.utils.Utils;

/**
 * @author Xujiayao
 */
public class DMCC {

	public static final String VERSION = Utils.getVersion();
	public static final Logger LOGGER = new Logger();

	public static void main(String[] args) {
		LOGGER.info("Hello, World!");

		init("Standalone");
	}

	public static void init(String loader) {
		LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);
	}
}
