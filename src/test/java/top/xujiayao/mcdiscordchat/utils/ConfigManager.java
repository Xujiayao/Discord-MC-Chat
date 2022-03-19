package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.xujiayao.mcdiscordchat.Config;
import top.xujiayao.mcdiscordchat.Main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author Xujiayao
 */
public class ConfigManager {

	private static File file;

	private ConfigManager() {
		throw new IllegalStateException("Utility class");
	}

	public static void initConfig() {
		file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat.json");

		if (file.exists() && file.length() != 0) {
			try {
				loadConfig();
			} catch (Exception e) {
				e.printStackTrace();
				Main.config = new Config();
			}

			updateConfig();

			Utils.reloadTextsConfig();
		} else {
			createConfig();

			Logger LOGGER = LogManager.getLogger();

			LOGGER.error("--------------------");
			LOGGER.error("错误：找不到配置文件或配置文件为空！");
			LOGGER.error("Error: The config file cannot be found or is empty!");
			LOGGER.error("");
			LOGGER.error("请在重新启动服务器前编辑 /config/mcdiscordchat.json 以配置 MCDiscordChat！");
			LOGGER.error("Please edit /config/mcdiscordchat.json to configure MCDiscordChat before restarting the server!");
			LOGGER.error("");
			LOGGER.error("正在停止服务器...");
			LOGGER.error("Stopping the server...");
			LOGGER.error("--------------------");

			System.exit(1);
		}
	}

	public static void loadConfig() throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String temp;
			StringBuilder jsonString = new StringBuilder();

			while ((temp = reader.readLine()) != null) {
				jsonString.append(temp);
			}

			Main.config = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.fromJson(jsonString.toString(), new TypeToken<Config>() {
					}.getType());
		}
	}

	private static void createConfig() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			String jsonString = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.toJson(new Config());

			writer.write(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void updateConfig() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			String jsonString = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.create()
					.toJson(Main.config);

			writer.write(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
