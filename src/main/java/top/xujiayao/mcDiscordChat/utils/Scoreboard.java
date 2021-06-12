package top.xujiayao.mcDiscordChat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import top.xujiayao.mcDiscordChat.Config;
import top.xujiayao.mcDiscordChat.objects.Player;
import top.xujiayao.mcDiscordChat.objects.Stats;

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
		BufferedReader reader = null;
		FileReader fileReader = null;

		StringBuilder output = null;

		try {
			String temp = message.replace("!scoreboard ", "");

			String type = temp.substring(0, temp.lastIndexOf(" ") - 1);
			String id = temp.substring(temp.indexOf(" ") + 1);

			reader = new BufferedReader(new FileReader(FabricLoader.getInstance().getGameDir().toAbsolutePath() + "/usercache.json"));

			String jsonString = reader.readLine();

			Gson gson = new Gson();
			Type userListType = new TypeToken<ArrayList<Player>>() {
			}.getType();

			Config.playerList = gson.fromJson(jsonString, userListType);

			Config.statsFileList = Utils.getFileList(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString().replace(".", "") + Config.worldName + "/stats/"));
			Config.statsList = new ArrayList<>();

			for (File file : Config.statsFileList) {
				fileReader = new FileReader(file);
				reader = new BufferedReader(fileReader);

				for (Player player : Config.playerList) {
					if (player.getUuid().equals(file.getName().replace(".json", ""))) {
						Config.statsList.add(new Stats(player.getName(), reader.readLine()));
					}
				}
			}

			Config.scoreboardMap = new HashMap<>();

			for (Stats stats : Config.statsList) {
				temp = stats.getContent();

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

				Config.scoreboardMap.put(stats.getName(), Integer.valueOf(temp));
			}

			List<Map.Entry<String, Integer>> entryList = new ArrayList<>(Config.scoreboardMap.entrySet());

			entryList.sort((o1, o2) -> (o2.getValue() - o1.getValue()));

			output = new StringBuilder("```\n=============== 排行榜 ===============\n");

			for (Map.Entry<String, Integer> entry : entryList) {
				output.append(String.format("\n%-8d %-8s", entry.getValue(), entry.getKey()));
			}

			output.append("```");

			reader.close();

			if (fileReader != null) {
				fileReader.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}

				if (fileReader != null) {
					fileReader.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (output == null) {
			output = new StringBuilder("```\n=============== 排行榜 ===============\n")
				  .append("\n无结果")
				  .append("\n```");
		}

		return output;
	}
}
