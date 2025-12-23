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
		DMCC.init();

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		TerminalManager.init();

		// Register shutdown hook for standalone mode
		Runtime.getRuntime().addShutdownHook(new Thread(DMCC::shutdown, "DMCC-Shutdown"));
	}
}
