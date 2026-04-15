package com.xujiayao.discord_mc_chat.standalone;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.utils.logging.impl.LoggerImpl;

/**
 * The entry point for Standalone environment.
 *
 * @author Xujiayao
 */
public final class StandaloneDMCC {

	/**
	 * Standalone shutdown hook thread that stops DMCC and logging.
	 */
	public static final Thread SHUTDOWN_THREAD = new Thread(() -> {
		DMCC.shutdown();

		// Logger cleanup
		LoggerImpl.shutdown();
	}, "DMCC-ShutdownHook");

	private StandaloneDMCC() {
	}

	/**
	 * Start Standalone DMCC.
	 *
	 * @param args Command line arguments
	 */
	static void main(String[] args) {
		for (String arg : args) {
			if ("--disable-ascii".equalsIgnoreCase(arg)) {
				LoggerImpl.setConsoleAnsiEnabled(false);
				break;
			}
		}

		// Register shutdown hook for standalone mode
		Runtime.getRuntime().addShutdownHook(SHUTDOWN_THREAD);

		// Initialize DMCC, block until initialization is complete
		if (DMCC.init()) {
			// Initialize terminal only if DMCC initialized successfully
			TerminalManager.init();
		} else {
			System.exit(-1);
		}
	}
}
