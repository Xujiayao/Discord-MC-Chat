package com.xujiayao.discord_mc_chat.logging.impl;

import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * DMCC Logger implementation.
 * <p>
 * Only the level checks, the caller name and the normalized logging call are implemented here; every
 * {@code trace}/{@code debug}/{@code info}/{@code warn}/{@code error} overload (including the {@link Marker}
 * variants) is provided by {@link LegacyAbstractLogger}.
 */
public final class LoggerImpl extends LegacyAbstractLogger {

	private static volatile PrintWriter fileWriter;
	private static boolean fileWriterInitialized = false;
	private static volatile boolean consoleAnsiEnabled = true;

	private final Object minecraftLogger;

	private final Map<String, Method> logMethods = new HashMap<>();
	private final Map<String, Method> logThrowMethods = new HashMap<>();

	/**
	 * Create a new Logger instance.
	 * <p>
	 * If running in a Minecraft environment, initializes the Minecraft logger via reflection.
	 * Otherwise, sets up for standard output logging.
	 */
	public LoggerImpl(String name) {
		this.name = name;

		if (EnvironmentUtils.isMinecraftEnvironment()) {
			try {
				String loggerClassName = "dmcc_dep.org.slf4j.Logger";
				String loggerFactoryClassName = "dmcc_dep.org.slf4j.LoggerFactory";

				Class<?> loggerClass = Class.forName(loggerClassName.replace("dmcc_dep.", ""));

				Class<?> loggerFactoryClass = Class.forName(loggerFactoryClassName.replace("dmcc_dep.", ""));
				this.minecraftLogger = loggerFactoryClass.getMethod("getLogger", String.class).invoke(null, "discord_mc_chat");

				logMethods.put("TRACE", loggerClass.getMethod("trace", String.class));
				logMethods.put("DEBUG", loggerClass.getMethod("debug", String.class));
				logMethods.put("INFO", loggerClass.getMethod("info", String.class));
				logMethods.put("WARN", loggerClass.getMethod("warn", String.class));
				logMethods.put("ERROR", loggerClass.getMethod("error", String.class));

				logThrowMethods.put("TRACE", loggerClass.getMethod("trace", String.class, Throwable.class));
				logThrowMethods.put("DEBUG", loggerClass.getMethod("debug", String.class, Throwable.class));
				logThrowMethods.put("INFO", loggerClass.getMethod("info", String.class, Throwable.class));
				logThrowMethods.put("WARN", loggerClass.getMethod("warn", String.class, Throwable.class));
				logThrowMethods.put("ERROR", loggerClass.getMethod("error", String.class, Throwable.class));
			} catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
			         NoSuchMethodException e) {
				throw new RuntimeException("Failed to initialize DMCC Logger", e);
			}
		} else {
			minecraftLogger = null;
			synchronized (LoggerImpl.class) {
				if (!fileWriterInitialized) {
					try {
						Files.createDirectories(Paths.get("logs"));
						String fileName = "logs/DMCC_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".log";
						fileWriter = new PrintWriter(new FileWriter(fileName, true), true);
					} catch (IOException e) {
						System.err.println("Failed to create log file: " + e.getMessage());
						e.printStackTrace(System.err);
					} finally {
						fileWriterInitialized = true;
					}
				}
			}
		}
	}

	/**
	 * Closes the file writer if it was initialized.
	 * <p>
	 * Only Standalone environment requires this cleanup.
	 */
	public static void shutdown() {
		if (fileWriter != null) {
			fileWriter.close();
		}
	}

	/**
	 * Enable or disable ANSI color output in standalone console logs.
	 *
	 * @param enabled true to enable ANSI color output, false to disable
	 */
	public static void setConsoleAnsiEnabled(boolean enabled) {
		consoleAnsiEnabled = enabled;
	}

	// Logging level checks
	// TRACE and DEBUG are intentionally disabled in DMCC: isTraceEnabled()/isDebugEnabled() always
	// return false, so the inherited trace()/debug() methods are deliberate no-ops.

	@Override
	public boolean isTraceEnabled() {
		return false;
	}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	protected String getFullyQualifiedCallerName() {
		return name;
	}

	@Override
	protected void handleNormalizedLoggingCall(Level level, Marker marker, String message, Object[] arguments, Throwable throwable) {
		// StringUtils.format() returns the message unchanged when there are no arguments, which is exactly
		// what the previous hand-written overloads did for the (msg) and (msg, throwable) forms.
		log(level.name(), StringUtils.format(message, arguments), throwable);
	}

	private void log(String level, String msg, Throwable t) {
		// Escaping collapses newlines in msg into the literal two-character sequence "\n",
		// so every log line stays a single physical line in both the file and the console.
		msg = StringUtils.escape(msg);

		if (EnvironmentUtils.isMinecraftEnvironment()) {
			try {
				if (t == null) {
					Method m = logMethods.get(level);
					m.invoke(minecraftLogger, msg);
				} else {
					Method m = logThrowMethods.get(level);
					m.invoke(minecraftLogger, msg, t);
				}
			} catch (InvocationTargetException | IllegalAccessException e) {
				throw new RuntimeException("Failed to log message: " + msg, e);
			}
		} else {
			String time = new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis());
			String thread = Thread.currentThread().getName();

			// 1. Log to File (Plain Text, no colors)
			if (fileWriter != null) {
				fileWriter.println(StringUtils.format("[{}] [{}/{}]: {}", time, thread, level, msg));
				if (t != null) {
					t.printStackTrace(fileWriter);
				}
			}

			// 2. Log to Console (ANSI colors are optional)
			String consoleLine;
			if (consoleAnsiEnabled) {
				String color = switch (level) {
					case "INFO" -> "\u001B[32m";
					case "WARN" -> "\u001B[33m";
					case "ERROR" -> "\u001B[31m";
					default -> "\u001B[0m";
				};
				consoleLine = StringUtils.format("[{}] [{}/{}{}\u001B[0m]: {}", time, thread, color, level, msg);
			} else {
				consoleLine = StringUtils.format("[{}] [{}/{}]: {}", time, thread, level, msg);
			}

			System.out.println(consoleLine);

			if (t != null) {
				t.printStackTrace(System.out);
			}
		}
	}
}
