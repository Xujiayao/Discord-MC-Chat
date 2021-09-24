package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import top.xujiayao.mcdiscordchat.Config;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.objects.Texts;

import java.io.*;

/**
 * @author Xujiayao
 */
public class ConfigManager {

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

			if (Main.config.generic.switchLanguageFromChinToEng) {
				Main.texts = new Texts(Main.config.textsEN.serverStarted,
					  Main.config.textsEN.serverStopped,
					  Main.config.textsEN.joinServer,
					  Main.config.textsEN.leftServer,
					  Main.config.textsEN.deathMessage,
					  Main.config.textsEN.advancementTask,
					  Main.config.textsEN.advancementChallenge,
					  Main.config.textsEN.advancementGoal,
					  Main.config.textsEN.coloredText,
					  Main.config.textsEN.colorlessText,
					  Main.config.textsEN.removeVanillaFormattingFromDiscord,
					  Main.config.textsEN.removeLineBreakFromDiscord);
			} else {
				Main.texts = new Texts(Main.config.textsZH.serverStarted,
					  Main.config.textsZH.serverStopped,
					  Main.config.textsZH.joinServer,
					  Main.config.textsZH.leftServer,
					  Main.config.textsZH.deathMessage,
					  Main.config.textsZH.advancementTask,
					  Main.config.textsZH.advancementChallenge,
					  Main.config.textsZH.advancementGoal,
					  Main.config.textsZH.coloredText,
					  Main.config.textsZH.colorlessText,
					  Main.config.textsZH.removeVanillaFormattingFromDiscord,
					  Main.config.textsZH.removeLineBreakFromDiscord);
			}
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

	public static void loadConfig() throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String temp;
			StringBuilder jsonString = new StringBuilder();

			while ((temp = reader.readLine()) != null) {
				jsonString.append(temp);
			}

			Main.config = new GsonBuilder()
				  .setPrettyPrinting()
				  .disableHtmlEscaping()
				  .excludeFieldsWithoutExposeAnnotation()
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
				  .excludeFieldsWithoutExposeAnnotation()
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
				  .excludeFieldsWithoutExposeAnnotation()
				  .create()
				  .toJson(Main.config);

			writer.write(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
