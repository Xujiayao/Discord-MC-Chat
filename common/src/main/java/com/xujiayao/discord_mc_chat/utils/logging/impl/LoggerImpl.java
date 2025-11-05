package com.xujiayao.discord_mc_chat.utils.logging.impl;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.xujiayao.discord_mc_chat.Constants.IS_MINECRAFT_ENV;

/**
 * DMCC Logger implementation.
 *
 * @author Xujiayao
 */
public class LoggerImpl implements Logger {

	private final String name;

	private final Object minecraftLogger;

	private final Map<String, Method> logMethods = new HashMap<>();
	private final Map<String, Method> logThrowMethods = new HashMap<>();

	private static volatile PrintWriter fileWriter;
	private static boolean fileWriterInitialized = false;

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

		if (IS_MINECRAFT_ENV) {
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
						System.out.println("Failed to create log file: " + e.getMessage());
						e.printStackTrace(System.out);
					} finally {
						fileWriterInitialized = true;
					}
				}
			}
		}
	}

	/**
	 * Closes the file writer if it was initialized.
	 */
	public static void closeFileWriter() {
		if (fileWriter != null) {
			fileWriter.close();
		}
	}

	@Override
	public String getName() {
		return name;
	}

	// Helper methods

	/**
	 * Log a message at the specified level, optionally with a throwable.
	 * <p>
	 * If running in a Minecraft environment, uses the Minecraft logger via reflection.
	 * Otherwise, logs to standard output in Minecraft style.
	 *
	 * @param level Logging level (TRACE, DEBUG, INFO, WARN, ERROR)
	 * @param msg   Message to log
	 * @param t     Throwable to log (can be null)
	 */
	private void log(String level, String msg, Throwable t) {
		msg = StringUtils.escape(msg);

		if (IS_MINECRAFT_ENV) {
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

			String formattedMessage = StringUtils.format("[{}] [{}/{}]: {}", time, thread, level, msg);

			System.out.println(formattedMessage);
			if (fileWriter != null) {
				fileWriter.println(formattedMessage);
			}

			if (t != null) {
				t.printStackTrace(System.out);
				if (fileWriter != null) {
					t.printStackTrace(fileWriter);
				}
			}
		}
	}

	/**
	 * Log a message at the specified level without a throwable.
	 *
	 * @param level Logging level (TRACE, DEBUG, INFO, WARN, ERROR)
	 * @param msg   Message to log
	 */
	private void log(String level, String msg) {
		log(level, msg, null);
	}

	// Logging level checks

	@Override
	public boolean isTraceEnabled() {
		return true;
	}

	@Override
	public boolean isDebugEnabled() {
		return true;
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
		return true;
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return true;
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

	// TRACE (no-operation)

	@Override
	public void trace(String msg) {
		// log("TRACE", msg);
	}

	@Override
	public void trace(String format, Object arg) {
		// log("TRACE", StringUtils.format(format, arg));
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		// log("TRACE", StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void trace(String format, Object... arguments) {
		// log("TRACE", StringUtils.format(format, arguments));
	}

	@Override
	public void trace(String msg, Throwable t) {
		// log("TRACE", msg, t);
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

	// DEBUG (no-operation)

	@Override
	public void debug(String msg) {
		// log("DEBUG", msg);
	}

	@Override
	public void debug(String format, Object arg) {
		// log("DEBUG", StringUtils.format(format, arg));
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		// log("DEBUG", StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void debug(String format, Object... arguments) {
		// log("DEBUG", StringUtils.format(format, arguments));
	}

	@Override
	public void debug(String msg, Throwable t) {
		// log("DEBUG", msg, t);
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
		log("INFO", msg);
	}

	@Override
	public void info(String format, Object arg) {
		log("INFO", StringUtils.format(format, arg));
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		log("INFO", StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void info(String format, Object... arguments) {
		log("INFO", StringUtils.format(format, arguments));
	}

	@Override
	public void info(String msg, Throwable t) {
		log("INFO", msg, t);
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
		log("WARN", msg);
	}

	@Override
	public void warn(String format, Object arg) {
		log("WARN", StringUtils.format(format, arg));
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		log("WARN", StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void warn(String format, Object... arguments) {
		log("WARN", StringUtils.format(format, arguments));
	}

	@Override
	public void warn(String msg, Throwable t) {
		log("WARN", msg, t);
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
		log("ERROR", msg);
	}

	@Override
	public void error(String format, Object arg) {
		log("ERROR", StringUtils.format(format, arg));
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		log("ERROR", StringUtils.format(format, arg1, arg2));
	}

	@Override
	public void error(String format, Object... arguments) {
		log("ERROR", StringUtils.format(format, arguments));
	}

	@Override
	public void error(String msg, Throwable t) {
		log("ERROR", msg, t);
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
}
