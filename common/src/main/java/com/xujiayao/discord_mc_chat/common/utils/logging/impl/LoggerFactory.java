/*
 * This file is modified from org.slf4j:slf4j-simple (https://github.com/qos-ch/slf4j/tree/v_2.0.17/slf4j-simple)
 * Original code is licensed under the MIT License.
 */

package com.xujiayao.discord_mc_chat.common.utils.logging.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Ceki G&uuml;lc&uuml;
 * @author Xujiayao
 */
public class LoggerFactory implements ILoggerFactory {

	ConcurrentMap<String, Logger> loggerMap;

	public LoggerFactory() {
		loggerMap = new ConcurrentHashMap<>();
	}

	public Logger getLogger(String name) {
		return loggerMap.computeIfAbsent(name, this::createLogger);
	}

	protected Logger createLogger(String name) {
		return new LoggerImpl(name);
	}

	protected void reset() {
		loggerMap.clear();
	}
}
