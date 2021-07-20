package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import top.xujiayao.mcdiscordchat.Config;
import top.xujiayao.mcdiscordchat.Main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * @author Xujiayao
 */
public class ConfigManager {

	private static File file;

	public static Config initConfig() {
		Config config = null;

		file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat.json");

		if (file.exists() && file.length() != 0) {
			config = loadConfig();
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

		return config;
	}

	private static Config loadConfig() {
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader(file));

			String temp;
			StringBuilder jsonString = new StringBuilder();

			while ((temp = reader.readLine()) != null) {
				jsonString.append(temp);
			}

			reader.close();

			return new Gson().fromJson(jsonString.toString(), new TypeToken<Config>() {
			}.getType());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return new Config();
	}

	private static void createConfig() {
		BufferedWriter writer = null;

		try {
			writer = new BufferedWriter(new FileWriter(file));

			String jsonString = new GsonBuilder()
				  .setPrettyPrinting()
				  .disableHtmlEscaping()
				  .create()
				  .toJson(new Config());

			writer.write(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void updateConfig() {
		BufferedWriter writer = null;

		try {
			writer = new BufferedWriter(new FileWriter(file));

			String jsonString = new GsonBuilder()
				  .setPrettyPrinting()
				  .disableHtmlEscaping()
				  .create()
				  .toJson(Main.config);

			writer.write(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
