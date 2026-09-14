/*
 * This file is modified from org.slf4j:slf4j-simple (https://github.com/qos-ch/slf4j/tree/v_2.0.17/slf4j-simple)
 * Original code is licensed under the MIT License.
 */

package com.xujiayao.discord_mc_chat.logging.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * slf4j-simple's implementation of {@link SLF4JServiceProvider}.
 * <p>
 * This provider only exists so that the standalone DMCC application can log without Minecraft present.
 * Its service registration therefore lives in {@code core/src/main/shadow-resources} and is added to the
 * shadow JAR only - never to {@code src/main/resources}. If it were on a Minecraft development classpath,
 * SLF4J would select it over the loader's own provider and the game would fail to start with
 * "Failed to initialize DMCC Logger" caused by a recursive class initialisation.
 *
 * @author Ceki Gülcü
 * @author Xujiayao
 */
public final class ServiceProvider implements SLF4JServiceProvider {

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

	/**
	 * Creates an SLF4J service provider instance.
	 */
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
