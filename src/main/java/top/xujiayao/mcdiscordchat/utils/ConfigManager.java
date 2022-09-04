package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.Config;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONFIG_BACKUP_FILE;
import static top.xujiayao.mcdiscordchat.Main.CONFIG_FILE;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class ConfigManager {

	public static void init(boolean throwException) throws Exception {
		if (CONFIG_FILE.length() != 0) {
			try {
				FileUtils.copyFile(CONFIG_FILE, CONFIG_BACKUP_FILE);

				load();

				try {
					if (!CONFIG.customMessage.formattedResponseMessage.isBlank()) {
						new Gson().fromJson(CONFIG.customMessage.formattedResponseMessage, Object.class);
					}
					if (!CONFIG.customMessage.formattedChatMessage.isBlank()) {
						new Gson().fromJson(CONFIG.customMessage.formattedChatMessage, Object.class);
					}
					if (!CONFIG.customMessage.formattedOtherMessage.isBlank()) {
						new Gson().fromJson(CONFIG.customMessage.formattedOtherMessage, Object.class);
					}
				} catch (JsonSyntaxException e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
					LOGGER.error("Invalid JSON!");
				}

				update();
			} catch (Exception e) {
				if (throwException) {
					throw e;
				}

				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		} else {
			create();

			LOGGER.error("-----------------------------------------");
			LOGGER.error("Error: The config file cannot be found or is empty!");
			LOGGER.error("");
			LOGGER.error("Please follow the documentation to configure MCDiscordChat before restarting the server!");
			LOGGER.error("More information + Docs: https://blog.xujiayao.top/posts/4ba0a17a/");
			LOGGER.error("");
			LOGGER.error("Stopping the server...");
			LOGGER.error("-----------------------------------------");

			System.exit(0);
		}
	}

	private static void create() {
		try (FileOutputStream outputStream = new FileOutputStream(CONFIG_FILE)) {
			String jsonString = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.toJson(new Config());

			IOUtils.write(jsonString, outputStream, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	private static void load() {
		try {
			CONFIG = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.fromJson(IOUtils.toString(CONFIG_FILE.toURI(), StandardCharsets.UTF_8), Config.class);
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static void update() {
		try (FileOutputStream outputStream = new FileOutputStream(CONFIG_FILE)) {
			String jsonString = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.toJson(CONFIG);

			IOUtils.write(jsonString, outputStream, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}
