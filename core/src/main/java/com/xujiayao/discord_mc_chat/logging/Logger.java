package com.xujiayao.discord_mc_chat.logging;

import org.slf4j.LoggerFactory;

/**
 * A simple logger wrapper around SLF4J for consistency between Minecraft and Standalone environments.
 *
 * @author Xujiayao
 */
public final class Logger {

	private final org.slf4j.Logger logger;

	public Logger() {
		logger = LoggerFactory.getLogger("discord_mc_chat");
	}

	public void info(String message, Object... args) {
		logger.info(message, args);
	}

	public void warn(String message, Object... args) {
		logger.warn(message, args);
	}

	public void error(String message, Object... args) {
		logger.error(message, args);
	}

	public void error(String message, Throwable t) {
		logger.error(message, t);
	}
}
