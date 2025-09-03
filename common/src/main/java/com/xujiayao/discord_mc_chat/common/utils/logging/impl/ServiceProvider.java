/*
 * This file is modified from org.slf4j:slf4j-simple (https://github.com/qos-ch/slf4j/tree/v_2.0.17/slf4j-simple)
 * Original code is licensed under the MIT License.
 */

package com.xujiayao.discord_mc_chat.common.utils.logging.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * slf4j-simple's implementation of {@link SLF4JServiceProvider}.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Xujiayao
 */
public class ServiceProvider implements SLF4JServiceProvider {

	/**
	 * Declare the version of the SLF4J API this implementation is compiled against.
	 * The value of this field is modified with each major release.
	 * <p>
	 * To avoid constant folding by the compiler, this field must *not* be final.
	 */
	public static String REQUESTED_API_VERSION = "2.0.99"; // !final

	private final IMarkerFactory markerFactory;
	private final MDCAdapter mdcAdapter;
	private ILoggerFactory loggerFactory;

	public ServiceProvider() {
		markerFactory = new BasicMarkerFactory();
		mdcAdapter = new NOPMDCAdapter();
	}

	public ILoggerFactory getLoggerFactory() {
		return loggerFactory;
	}

	@Override
	public IMarkerFactory getMarkerFactory() {
		return markerFactory;
	}

	@Override
	public MDCAdapter getMDCAdapter() {
		return mdcAdapter;
	}

	@Override
	public String getRequestedApiVersion() {
		return REQUESTED_API_VERSION;
	}

	@Override
	public void initialize() {
		loggerFactory = new LoggerFactory();
	}
}
