package com.xujiayao.discord_mc_chat.utils.logging;

import org.slf4j.LoggerFactory;

/**
 * A simple logger wrapper around SLF4J for consistency between Minecraft and Standalone environments.
 *
 * @author Xujiayao
 */
public class Logger {

	private final org.slf4j.Logger logger;

	/**
	 * Create a logger instance with the name "discord_mc_chat"
	 */
	public Logger() {
		logger = LoggerFactory.getLogger("discord_mc_chat");
	}

	/**
	 * Log an info message
	 *
	 * @param message The message to log
	 * @param args    The arguments to format the message
	 */
	public void info(String message, Object... args) {
		logger.info(message, args);
	}

	/**
	 * Log a warning message
	 *
	 * @param message The message to log
	 * @param args    The arguments to format the message
	 */
	public void warn(String message, Object... args) {
		logger.warn(message, args);
	}

	/**
	 * Log an error message
	 *
	 * @param message The message to log
	 * @param args    The arguments to format the message
	 */
	public void error(String message, Object... args) {
		logger.error(message, args);
	}

	/**
	 * Log an error message with a throwable
	 *
	 * @param message The message to log
	 * @param t       The throwable to log
	 */
	public void error(String message, Throwable t) {
		logger.error(message, t);
	}
}
