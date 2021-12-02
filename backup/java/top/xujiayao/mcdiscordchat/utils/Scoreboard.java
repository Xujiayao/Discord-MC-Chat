package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.objects.Player;
import top.xujiayao.mcdiscordchat.objects.Stats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Xujiayao
 */
public class Scoreboard {

	public static StringBuilder getScoreboard(String message) {
		StringBuilder output = null;

		try {
			String temp = message.replace("!scoreboard ", "");

			String type = temp.substring(0, temp.lastIndexOf(" ") - 1);
			String id = temp.substring(temp.indexOf(" ") + 1);

			String jsonString;

			try (BufferedReader reader = new BufferedReader(new FileReader(FabricLoader.getInstance().getGameDir().toAbsolutePath() + "/usercache.json"))) {
				jsonString = reader.readLine();
			}

			Gson gson = new Gson();
			Type userListType = new TypeToken<ArrayList<Player>>() {
			}.getType();

			List<Player> playerList = gson.fromJson(jsonString, userListType);

			List<File> statsFileList = Utils.getFileList(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString().replace(".", "") + Main.config.generic.worldName + "/stats/"));
			List<Stats> statsList = new ArrayList<>();

			for (File file : statsFileList) {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					for (Player player : playerList) {
						if (player.uuid().equals(file.getName().replace(".json", ""))) {
							statsList.add(new Stats(player.name(), reader.readLine()));
						}
					}
				}
			}

			HashMap<String, Integer> scoreboardMap = new HashMap<>();

			for (Stats stats : statsList) {
				temp = stats.content();

				if (!temp.contains("minecraft:" + type)) {
					continue;
				}

				temp = temp.substring(temp.indexOf("minecraft:" + type));
				temp = temp.substring(0, temp.indexOf("}"));

				if (!temp.contains("minecraft:" + id)) {
					continue;
				}

				temp = temp.substring(temp.indexOf("minecraft:" + id) + ("minecraft:" + id).length() + 2);

				if (temp.contains(",")) {
					temp = temp.substring(0, temp.indexOf(","));
				}

				scoreboardMap.put(stats.name(), Integer.valueOf(temp));
			}

			List<Map.Entry<String, Integer>> entryList = new ArrayList<>(scoreboardMap.entrySet());

			entryList.sort((o1, o2) -> (o2.getValue() - o1.getValue()));

			output = new StringBuilder("```\n=============== " + (Main.config.generic.switchLanguageFromChinToEng ? "Scoreboard" : "排行榜") + " ===============\n");
			int length = 0;

			for (Map.Entry<String, Integer> entry : entryList) {
				output.append(String.format("\n%-8d %-8s", entry.getValue(), entry.getKey()));
				length++;
			}

			if (length == 0) {
				output.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "No result" : "无结果");
			}

			output.append("\n```");
		} catch (Exception e) {
			e.printStackTrace();

			if (ExceptionUtils.getStackTrace(e).length() > 1900) {
				Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(e).substring(0, 1800) + "\n```").queue();
				Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(e).substring(1800) + "\n```").queue();
			} else {
				Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(e) + "\n```").queue();
			}
		}

		if (output == null) {
			output = new StringBuilder("```\n=============== " + (Main.config.generic.switchLanguageFromChinToEng ? "Scoreboard" : "排行榜") + " ===============\n").append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "No result" : "无结果")
				  .append("\n```");
		}

		return output;
	}
}
