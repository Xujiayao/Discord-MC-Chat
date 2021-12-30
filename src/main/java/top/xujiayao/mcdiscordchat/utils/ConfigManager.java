package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.api.FabricLoader;
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

	private ConfigManager() {
		throw new IllegalStateException("Utility class");
	}

	private static File file;

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

			System.err.println("--------------------");
			System.err.println("错误：找不到配置文件或配置文件为空！");
			System.err.println("Error: The config file cannot be found or is empty!");
			System.err.println();
			System.err.println("请在重新启动服务器前编辑 /config/mcdiscordchat.json 以配置 MCDiscordChat！");
			System.err.println("Please edit /config/mcdiscordchat.json to configure MCDiscordChat before restarting the server!");
			System.err.println();
			System.err.println("正在停止服务器...");
			System.err.println("Stopping the server...");
			System.err.println("--------------------");

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
