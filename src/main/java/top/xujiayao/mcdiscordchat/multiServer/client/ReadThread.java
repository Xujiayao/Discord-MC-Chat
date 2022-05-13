package top.xujiayao.mcdiscordchat.multiServer.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER_STARTED_TIME;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
public class ReadThread extends Thread {

	private final Socket socket;
	private BufferedReader reader;

	public ReadThread(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

		while (true) {
			try {
				JsonObject json = new Gson().fromJson(reader.readLine(), JsonObject.class);

				if (json.get("special").getAsBoolean()) {
					JsonObject message = new Gson().fromJson(json.get("message").getAsString(), JsonObject.class);
					if (message.get("type").getAsString().equals("info")) {
						CHANNEL.sendMessage(Utils.getInfoCommandMessage()).queue();
					} else if (message.get("type").getAsString().equals("updateChannelTopic")) {
						JsonObject channelTopicInfo = new JsonObject();
						channelTopicInfo.addProperty("onlinePlayerCount", SERVER.getPlayerManager().getPlayerList().size());
						channelTopicInfo.addProperty("maxPlayerCount", SERVER.getPlayerManager().getMaxPlayerCount());

						Set<String> uniquePlayers = new HashSet<>();
						FileUtils.listFiles(new File((SERVER.getSaveProperties().getLevelName() + "/stats/")), null, false).forEach(file -> uniquePlayers.add(file.getName()));
						channelTopicInfo.add("uniquePlayers", new Gson().fromJson(Arrays.toString(uniquePlayers.toArray()), JsonArray.class));

						channelTopicInfo.addProperty("serverName", CONFIG.multiServer.name);
						channelTopicInfo.addProperty("serverStartedTime", SERVER_STARTED_TIME);

						MULTI_SERVER.client.writeThread.write("channelTopicInfo" + channelTopicInfo);
					}
					// Switch
					continue;
				}

				if (json.get("isChat").getAsBoolean()) {
					Utils.sendConsoleMessage(TEXTS.unformattedChatMessage()
							.replace("%server%", json.get("serverName").getAsString())
							.replace("%name%", json.get("playerName").getAsString())
							.replace("%message%", json.get("message").getAsString()));

					Text text = Text.Serializer.fromJson(TEXTS.formattedChatMessage()
							.replace("%server%", json.get("serverName").getAsString())
							.replace("%name%", json.get("playerName").getAsString())
							.replace("%roleColor%", "white")
							.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

					SERVER.getPlayerManager().getPlayerList().forEach(
							player -> player.sendMessage(text, false));
				} else {
					Utils.sendConsoleMessage(TEXTS.unformattedOtherMessage()
							.replace("%server%", json.get("serverName").getAsString())
							.replace("%message%", json.get("message").getAsString()));

					Text text = Text.Serializer.fromJson(TEXTS.formattedOtherMessage()
							.replace("%server%", json.get("serverName").getAsString())
							.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

					SERVER.getPlayerManager().getPlayerList().forEach(
							player -> player.sendMessage(text, false));
				}
			} catch (Exception e) {
				break;
			}
		}
	}
}