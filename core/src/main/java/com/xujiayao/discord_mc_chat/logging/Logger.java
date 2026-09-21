package com.xujiayao.discord_mc_chat.logging;

import org.slf4j.LoggerFactory;

/**
 * A simple logger wrapper around SLF4J for consistency between Minecraft and Standalone environments.
 */
public final class Logger {

	private final org.slf4j.Logger logger;

	public Logger() {
		logger = LoggerFactory.getLogger("discord_mc_chat");
	}

	public void info(String message, Object... args) {
		logger.info(message, args);
	}

	// Explicit (String, Throwable) overloads: without them such a call binds to the Object... overload and
	// relies on the SLF4J binding detecting the trailing Throwable inside the varargs array.
	public void info(String message, Throwable t) {
		logger.info(message, t);
	}

	public void warn(String message, Object... args) {
		logger.warn(message, args);
	}

	public void warn(String message, Throwable t) {
		logger.warn(message, t);
	}

	public void error(String message, Object... args) {
		logger.error(message, args);
	}

	public void error(String message, Throwable t) {
		logger.error(message, t);
	}
}
