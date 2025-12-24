package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.DMCC;

/**
 * The entry point for Standalone environment.
 *
 * @author Xujiayao
 */
public class StandaloneDMCC {

	/**
	 * Start Standalone DMCC.
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		// Register shutdown hook for standalone mode
		Runtime.getRuntime().addShutdownHook(new Thread(DMCC::shutdown, "DMCC-ShutdownHook"));

		// Initialize DMCC, block until initialization is complete
		if (DMCC.init()) {
			// Initialize terminal only if DMCC initialized successfully
			TerminalManager.init();
		}
	}
}
