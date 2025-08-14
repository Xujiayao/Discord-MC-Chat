package com.xujiayao.discord_mc_chat.common.utils.logging.impl;

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Xujiayao
 */
public class LoggerImpl implements Logger {

	private final String name;
	private final boolean isMinecraftEnvironment;

	private final Object minecraftLogger;

	private final Map<String, Method> logMethods = new HashMap<>();
	private final Map<String, Method> logThrowMethods = new HashMap<>();

	public LoggerImpl(String name) {
		this.name = name;
		this.isMinecraftEnvironment = isMinecraftEnvironment();

		if (isMinecraftEnvironment) {
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
		}
	}

	private boolean isMinecraftEnvironment() {
		// Fabric
		try {
			Class.forName("net.fabricmc.loader.api.FabricLoader");
			return true;
		} catch (ClassNotFoundException ignored) {
		}

		// NeoForge
		try {
			Class.forName("net.neoforged.fml.loading.FMLLoader");
			return true;
		} catch (ClassNotFoundException ignored) {
		}

		return false;
	}

	@Override
	public String getName() {
		return name;
	}

	// Helper methods
	private void log(String level, String msg, Throwable t) {
		if (isMinecraftEnvironment) {
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

			System.out.println(format("[{}] [{}/{}]: {}", time, thread, level, msg));

			if (t != null) {
				t.printStackTrace(System.out);
			}
		}
	}

	private void log(String level, String msg) {
		log(level, msg, null);
	}

	// Simple {} placeholder replacement
	private String format(String str, Object... args) {
		for (Object arg : args) {
			str = str.replaceFirst("\\{}", arg == null ? "null" : arg.toString());
		}
		return str;
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
		// log("TRACE", format(format, arg));
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		// log("TRACE", format(format, arg1, arg2));
	}

	@Override
	public void trace(String format, Object... arguments) {
		// log("TRACE", format(format, arguments));
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
		// log("DEBUG", format(format, arg));
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		// log("DEBUG", format(format, arg1, arg2));
	}

	@Override
	public void debug(String format, Object... arguments) {
		// log("DEBUG", format(format, arguments));
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
		log("INFO", format(format, arg));
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		log("INFO", format(format, arg1, arg2));
	}

	@Override
	public void info(String format, Object... arguments) {
		log("INFO", format(format, arguments));
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
		log("WARN", format(format, arg));
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		log("WARN", format(format, arg1, arg2));
	}

	@Override
	public void warn(String format, Object... arguments) {
		log("WARN", format(format, arguments));
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
		log("ERROR", format(format, arg));
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		log("ERROR", format(format, arg1, arg2));
	}

	@Override
	public void error(String format, Object... arguments) {
		log("ERROR", format(format, arguments));
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
