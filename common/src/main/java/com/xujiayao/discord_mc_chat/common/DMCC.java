package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.utils.Utils;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * @author Xujiayao
 */
public class DMCC {

	public static final String VERSION = Utils.getVersion();
	public static final Logger LOGGER = new Logger();

	public static void init(String loader) {
		LOGGER.info("Initializing DMCC {} with loader: {}", VERSION, loader);
	}
}
