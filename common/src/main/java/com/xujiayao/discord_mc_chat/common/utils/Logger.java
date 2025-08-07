package com.xujiayao.discord_mc_chat.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * @author Xujiayao
 */
public class Logger {

	private final Object logger;

	private final Method infoMethod;
	private final Method warnMethod;
	private final Method errorMethod;

	public Logger() {
		String loggerClassName = "dmcc_dep.org.slf4j.Logger";
		String loggerFactoryClassName = "dmcc_dep.org.slf4j.LoggerFactory";

		try {
			Class<?> loggerClass;

			if (isMinecraftEnvironment()) {
				loggerClass = Class.forName(loggerClassName.replace("dmcc_dep.", ""));

				Class<?> loggerFactoryClass = Class.forName(loggerFactoryClassName.replace("dmcc_dep.", ""));
				this.logger = loggerFactoryClass.getMethod("getLogger", String.class).invoke(null, "discord_mc_chat");
			} else {
				loggerClass = org.slf4j.Logger.class;

				this.logger = org.slf4j.LoggerFactory.getLogger("discord_mc_chat");

				java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("");
				for (Handler handler : julLogger.getHandlers()) {
					handler.setFormatter(new CustomFormatter());
				}
			}

			this.infoMethod = loggerClass.getMethod("info", String.class, Object[].class);
			this.warnMethod = loggerClass.getMethod("warn", String.class, Object[].class);
			this.errorMethod = loggerClass.getMethod("error", String.class, Object[].class);
		} catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException |
				 IllegalAccessException e) {
			throw new RuntimeException("TODO", e);
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

	public void info(String message, Object... args) {
		try {
			infoMethod.invoke(logger, message, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException("TODO", e);
		}
	}

	public void warn(String message, Object... args) {
		try {
			warnMethod.invoke(logger, message, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException("TODO", e);
		}
	}

	public void error(String message, Object... args) {
		try {
			errorMethod.invoke(logger, message, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException("TODO", e);
		}
	}

	private static class CustomFormatter extends Formatter {
		@Override
		public String format(LogRecord record) {
			String time = new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis());
			String thread = Thread.currentThread().getName();
			String level = record.getLevel().getName().replace("SEVERE", "ERROR");
			String msg = formatMessage(record);

			return String.format("[%s] [%s/%s]: %s%n", time, thread, level, msg);
		}
	}
}
