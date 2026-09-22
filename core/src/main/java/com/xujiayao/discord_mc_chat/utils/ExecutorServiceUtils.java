package com.xujiayao.discord_mc_chat.utils;

import com.xujiayao.discord_mc_chat.config.ConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for ExecutorService related operations.
 *
 * @author Xujiayao
 */
public final class ExecutorServiceUtils {

	private ExecutorServiceUtils() {
	}

	/**
	 * Creates a ThreadFactory that ensures all created threads inherit the current Mod ClassLoader.
	 * This fixes issues with SLF4J/ServiceLoader not finding resources in async threads within Minecraft.
	 */
	public static ThreadFactory newThreadFactory(String name) {
		// Capture the correct ClassLoader NOW (while we are on the main/mod thread)
		final ClassLoader modClassLoader = ExecutorServiceUtils.class.getClassLoader();

		return r -> {
			Thread t = new Thread(r, name);
			// Force the new thread to use the Mod ClassLoader captured above
			t.setContextClassLoader(modClassLoader);
			return t;
		};
	}

	/**
	 * Shuts down the given ExecutorService gracefully based on configuration.
	 */
	public static void shutdownAnExecutor(ExecutorService executor) {
		executor.shutdown();
		try {
			if (ConfigManager.getBoolean("shutdown.graceful_shutdown", false)) {
				// Allow up to 10 minutes for ongoing requests to complete
				executor.awaitTermination(10, TimeUnit.MINUTES);
			} else {
				// Allow up to 5 seconds for ongoing requests to complete
				executor.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			// Restore the interrupt status: the shutdown hook must not swallow the interruption.
			Thread.currentThread().interrupt();
		} finally {
			// Whatever is still running after the grace period must not keep the JVM alive.
			executor.shutdownNow();
		}
	}
}
