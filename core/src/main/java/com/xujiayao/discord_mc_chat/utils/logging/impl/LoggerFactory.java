/*
 * This file is modified from org.slf4j:slf4j-simple (https://github.com/qos-ch/slf4j/tree/v_2.0.17/slf4j-simple)
 * Original code is licensed under the MIT License.
 */

package com.xujiayao.discord_mc_chat.utils.logging.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An implementation of {@link ILoggerFactory} which always returns
 * {@link LoggerImpl} instances.
 *
 * @author Ceki Gülcü
 * @author Xujiayao
 */
public class LoggerFactory implements ILoggerFactory {

	ConcurrentMap<String, Logger> loggerMap;

	public LoggerFactory() {
		loggerMap = new ConcurrentHashMap<>();
	}

	/**
	 * Return an appropriate {@link LoggerImpl} instance by name.
	 * <p>
	 * This method will call {@link #createLogger(String)} if the logger
	 * has not been created yet.
	 *
	 * @param name The name of the logger to retrieve
	 * @return The logger instance
	 */
	public Logger getLogger(String name) {
		return loggerMap.computeIfAbsent(name, this::createLogger);
	}

	/**
	 * Actually creates the logger for the given name.
	 *
	 * @param name The name of the logger to create
	 * @return The newly created logger
	 */
	protected Logger createLogger(String name) {
		return new LoggerImpl(name);
	}
}
