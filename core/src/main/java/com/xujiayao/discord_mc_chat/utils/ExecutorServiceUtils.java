package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for ExecutorService related operations.
 *
 * @author Xujiayao
 */
public class ExecutorServiceUtils {

	/**
	 * Shuts down the given ExecutorService gracefully based on configuration.
	 *
	 * @param executor the ExecutorService to shut down
	 */
	public static void shutdownAnExecutor(ExecutorService executor) {
		executor.shutdown();
		try {
			if (ConfigManager.getBoolean("shutdown.graceful_shutdown")) {
				// Allow up to 10 minutes for ongoing requests to complete
				boolean ignored = executor.awaitTermination(10, TimeUnit.MINUTES);
			} else {
				// Allow up to 5 seconds for ongoing requests to complete
				boolean ignored = executor.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (Exception ignored) {
		}
		executor.shutdownNow();
	}
}
