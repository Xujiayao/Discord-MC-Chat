package com.xujiayao.discord_mc_chat.logging.impl;

import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * DMCC Logger implementation.
 *
 * @author Xujiayao
 */
public final class LoggerImpl implements Logger {

	/**
	 * Timestamp format of every console and file log line.
	 */
	private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	/**
	 * Timestamp format of the log file name.
	 */
	private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static volatile PrintWriter fileWriter;
	private static volatile boolean fileWriterInitialized = false;
	private static volatile boolean consoleAnsiEnabled = true;

	private final String name;

	private final Object minecraftLogger;

	/**
	 * Create a new Logger instance.
	 * <p>
	 * If running in a Minecraft environment, initializes the Minecraft logger via reflection.
	 * Otherwise, sets up for standard output logging.
	 *
	 * @param name Logger name
	 */
	public LoggerImpl(String name) {
		this.name = name;

		if (!EnvironmentUtils.isMinecraftEnvironment()) {
			this.minecraftLogger = null;
			return;
		}

		try {
			// Shadow relocates DMCC's own classes - and the string constants that look like class names -
			// under "dmcc_dep.". The literals below are therefore written pre-relocated and stripped again
			// at runtime, so that Class.forName resolves MINECRAFT's own SLF4J instead of DMCC's bundled,
			// relocated copy. Loading the bundled copy here would route every message back into this class.
			// Do NOT "simplify" these two lines into plain Class.forName("org.slf4j.Logger") calls.
			String loggerClassName = "dmcc_dep.org.slf4j.Logger".replace("dmcc_dep.", "");
			String loggerFactoryClassName = "dmcc_dep.org.slf4j.LoggerFactory".replace("dmcc_dep.", "");

			Class<?> loggerClass = Class.forName(loggerClassName);
			Class<?> loggerFactoryClass = Class.forName(loggerFactoryClassName);
			this.minecraftLogger = loggerFactoryClass.getMethod("getLogger", String.class).invoke(null, "discord_mc_chat");
			for (Level level : Level.values()) {
				level.bind(loggerClass);
			}
		} catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
				 NoSuchMethodException e) {
			throw new RuntimeException("Failed to initialize DMCC Logger", e);
		}
	}

	/**
	 * Returns the log file writer, creating {@code logs/DMCC_<timestamp>.log} on first use.
	 * <p>
	 * The file is deliberately not created together with the logger: obtaining a logger - which the
	 * SLF4J service loader, tooling and unit tests do without ever logging a message - must not leave an
	 * empty {@code logs/} directory behind.
	 *
	 * @return The log file writer, or {@code null} if it could not be created
	 */
	private static PrintWriter fileWriter() {
		if (!fileWriterInitialized) {
			synchronized (LoggerImpl.class) {
				if (!fileWriterInitialized) {
					try {
						Files.createDirectories(Paths.get("logs"));
						String fileName = "logs/DMCC_" + LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT) + ".log";
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
		return fileWriter;
	}

	/**
	 * Closes the log file. Only the standalone environment needs this cleanup.
	 */
	public static void shutdown() {
		if (fileWriter != null) {
			fileWriter.close();
		}
	}

	/**
	 * @param enabled true to colour standalone console output with ANSI escapes, false for plain text.
	 */
	public static void setConsoleAnsiEnabled(boolean enabled) {
		consoleAnsiEnabled = enabled;
	}

	@Override
	public String getName() {
		return name;
	}

	// Helper methods

	private void log(Level level, String msg, Throwable t) {
		msg = StringUtils.escape(msg);

		if (EnvironmentUtils.isMinecraftEnvironment()) {
			try {
				Method method = t == null ? level.plain : level.withThrowable;
				if (t == null) {
					method.invoke(minecraftLogger, msg);
				} else {
					method.invoke(minecraftLogger, msg, t);
				}
			} catch (InvocationTargetException | IllegalAccessException e) {
				throw new RuntimeException("Failed to log message: " + msg, e);
			}
		} else {
			String time = LocalTime.now().format(LOG_TIME_FORMAT);
			String thread = Thread.currentThread().getName();

			// 1. Log to File (Plain Text, no colors)
			PrintWriter writer = fileWriter();
			if (writer != null) {
				writer.println(StringUtils.format("[{}] [{}/{}]: {}", time, thread, level.name(), msg));
				if (t != null) {
					t.printStackTrace(writer);
				}
			}

			// 2. Log to Console (ANSI colors are optional)
			String consoleLine;
			if (consoleAnsiEnabled) {
				consoleLine = StringUtils.format("[{}] [{}/{}{}\u001B[0m]: {}", time, thread, level.ansiColor, level.name(), msg);
			} else {
				consoleLine = StringUtils.format("[{}] [{}/{}]: {}", time, thread, level.name(), msg);
			}

			System.out.println(consoleLine);

			if (t != null) {
				t.printStackTrace(System.out);
			}
		}
	}

	private void log(Level level, String msg) {
		log(level, msg, null);
	}

	// Logging level checks

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
	public boolean isTraceEnabled(Marker marker) {
		return false;
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return false;
	}

	@Override
	public boolean isInfoEnabled(Marker marker) {
		return true;
	}

	@Override
	public boolean isWarnEnabled(Marker marker) {
		return true;
	}

	@Override
	public boolean isErrorEnabled(Marker marker) {
		return true;
	}

	// TRACE and DEBUG are intentionally no-ops: DMCC never emits them, and skipping the call keeps
	// argument formatting off the hot path. isTraceEnabled/isDebugEnabled report false so callers skip them.

	@Override
	public void trace(String msg) {
	}

	@Override
	public void trace(String format, Object arg) {
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
	}

	@Override
	public void trace(String format, Object... arguments) {
	}

	@Override
	public void trace(String msg, Throwable t) {
	}

	@Override
	public void trace(Marker marker, String msg) {
		trace(msg);
	}

	@Override
	public void trace(Marker marker, String format, Object arg) {
		trace(format, arg);
	}

	@Override
	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		trace(format, arg1, arg2);
	}

	@Override
	public void trace(Marker marker, String format, Object... arguments) {
		trace(format, arguments);
	}

	@Override
	public void trace(Marker marker, String msg, Throwable t) {
		trace(msg, t);
	}

	@Override
	public void debug(String msg) {
	}

	@Override
	public void debug(String format, Object arg) {
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
	}

	@Override
	public void debug(String format, Object... arguments) {
	}

	@Override
	public void debug(String msg, Throwable t) {
	}

	@Override
	public void debug(Marker marker, String msg) {
		debug(msg);
	}

	@Override
	public void debug(Marker marker, String format, Object arg) {
		debug(format, arg);
	}

	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		debug(format, arg1, arg2);
	}

	@Override
	public void debug(Marker marker, String format, Object... arguments) {
		debug(format, arguments);
	}

	@Override
	public void debug(Marker marker, String msg, Throwable t) {
		debug(msg, t);
	}

	// INFO
	@Override
	public void info(String msg) {
		log(Level.INFO, msg);
	}

	@Override
	public void info(String format, Object arg) {
		log(Level.INFO, StringUtils.format(format, arg));
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		log(Level.INFO, StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void info(String format, Object... arguments) {
		log(Level.INFO, StringUtils.format(format, arguments));
	}

	@Override
	public void info(String msg, Throwable t) {
		log(Level.INFO, msg, t);
	}

	@Override
	public void info(Marker marker, String msg) {
		info(msg);
	}

	@Override
	public void info(Marker marker, String format, Object arg) {
		info(format, arg);
	}

	@Override
	public void info(Marker marker, String format, Object arg1, Object arg2) {
		info(format, arg1, arg2);
	}

	@Override
	public void info(Marker marker, String format, Object... arguments) {
		info(format, arguments);
	}

	@Override
	public void info(Marker marker, String msg, Throwable t) {
		info(msg, t);
	}

	// WARN
	@Override
	public void warn(String msg) {
		log(Level.WARN, msg);
	}

	@Override
	public void warn(String format, Object arg) {
		log(Level.WARN, StringUtils.format(format, arg));
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		log(Level.WARN, StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void warn(String format, Object... arguments) {
		log(Level.WARN, StringUtils.format(format, arguments));
	}

	@Override
	public void warn(String msg, Throwable t) {
		log(Level.WARN, msg, t);
	}

	@Override
	public void warn(Marker marker, String msg) {
		warn(msg);
	}

	@Override
	public void warn(Marker marker, String format, Object arg) {
		warn(format, arg);
	}

	@Override
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		warn(format, arg1, arg2);
	}

	@Override
	public void warn(Marker marker, String format, Object... arguments) {
		warn(format, arguments);
	}

	@Override
	public void warn(Marker marker, String msg, Throwable t) {
		warn(msg, t);
	}

	// ERROR
	@Override
	public void error(String msg) {
		log(Level.ERROR, msg);
	}

	@Override
	public void error(String format, Object arg) {
		log(Level.ERROR, StringUtils.format(format, arg));
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		log(Level.ERROR, StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void error(String format, Object... arguments) {
		log(Level.ERROR, StringUtils.format(format, arguments));
	}

	@Override
	public void error(String msg, Throwable t) {
		log(Level.ERROR, msg, t);
	}

	@Override
	public void error(Marker marker, String msg) {
		error(msg);
	}

	@Override
	public void error(Marker marker, String format, Object arg) {
		error(format, arg);
	}

	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		error(format, arg1, arg2);
	}

	@Override
	public void error(Marker marker, String format, Object... arguments) {
		error(format, arguments);
	}

	@Override
	public void error(Marker marker, String msg, Throwable t) {
		error(msg, t);
	}

	/**
	 * The log levels DMCC emits.
	 * <p>
	 * Each constant caches its own ANSI colour and - in a Minecraft environment - the two reflective
	 * {@code slf4j.Logger} methods it needs, so a log call is a single field read instead of a
	 * {@code Map<String, Method>} lookup by level name.
	 */
	private enum Level {
		TRACE("\u001B[0m"),
		DEBUG("\u001B[0m"),
		INFO("\u001B[32m"),
		WARN("\u001B[33m"),
		ERROR("\u001B[31m");

		private final String ansiColor;
		private volatile Method plain;
		private volatile Method withThrowable;

		Level(String ansiColor) {
			this.ansiColor = ansiColor;
		}

		/**
		 * Resolves and caches this level's methods on the given SLF4J logger class.
		 *
		 * @param loggerClass The class obtained through reflection.
		 * @throws NoSuchMethodException If the class does not expose the expected overloads.
		 */
		private void bind(Class<?> loggerClass) throws NoSuchMethodException {
			String method = name().toLowerCase(Locale.ROOT);
			this.plain = loggerClass.getMethod(method, String.class);
			this.withThrowable = loggerClass.getMethod(method, String.class, Throwable.class);
		}
	}
}
