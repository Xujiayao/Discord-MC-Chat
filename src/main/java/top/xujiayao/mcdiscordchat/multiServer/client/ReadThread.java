package top.xujiayao.mcdiscordchat.multiServer.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
//#if MC <= 11802
//$$ import net.minecraft.text.LiteralText;
//#endif
import net.minecraft.text.Text;
//#if MC >= 11900
import net.minecraft.text.Texts;
//#endif
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Translations;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
//#if MC >= 11900
import java.util.ArrayList;
//#endif
import java.util.Arrays;
import java.util.HashSet;
//#if MC >= 11900
import java.util.List;
//#endif
import java.util.Objects;
import java.util.Set;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER_STARTED_TIME;

/**
 * @author Xujiayao
 */
public class ReadThread extends Thread {

	private final Socket socket;

	public ReadThread(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

			while (true) {
				try {
					JsonObject json = new Gson().fromJson(reader.readLine(), JsonObject.class);

					if (json.get("special").getAsBoolean()) {
						JsonObject message = new Gson().fromJson(json.get("message").getAsString(), JsonObject.class);
						if (message.get("type").getAsString().equals("discordInfoCommand")) {
							TextChannel channel = JDA.getTextChannelById(message.get("channel").getAsString());
							Objects.requireNonNull(channel).sendMessage("```\n" + Utils.getInfoCommandMessage() + "\n```").queue();
						} else if (message.get("type").getAsString().equals("updateChannelTopic")) {
							JsonObject channelTopicInfo = new JsonObject();
							channelTopicInfo.addProperty("onlinePlayerCount", SERVER.getPlayerManager().getPlayerList().size());
							channelTopicInfo.addProperty("maxPlayerCount", SERVER.getPlayerManager().getMaxPlayerCount());

							Set<String> uniquePlayers = new HashSet<>();
							//#if MC >= 11600
							FileUtils.listFiles(new File((SERVER.getSaveProperties().getLevelName() + "/stats/")), null, false).forEach(file -> uniquePlayers.add(file.getName()));
							//#else
							//$$ FileUtils.listFiles(new File((SERVER.getLevelName() + "/stats/")), null, false).forEach(file -> uniquePlayers.add(file.getName()));
							//#endif
							channelTopicInfo.add("uniquePlayers", new Gson().fromJson(Arrays.toString(uniquePlayers.toArray()), JsonArray.class));

							channelTopicInfo.addProperty("serverName", CONFIG.multiServer.name);
							channelTopicInfo.addProperty("serverStartedTime", SERVER_STARTED_TIME);

							MULTI_SERVER.client.writeThread.write("channelTopicInfo" + channelTopicInfo);
						}
						// Switch
						continue;
					}

					if (json.get("isChat").getAsBoolean()) {
						LOGGER.info(Translations.translateMessage("message.unformattedChatMessage")
								.replace("%server%", json.get("serverName").getAsString())
								.replace("%name%", json.get("playerName").getAsString())
								.replace("%message%", json.get("message").getAsString()));

						Text text = Text.Serializer.fromJson(Translations.translateMessage("message.formattedChatMessage")
								.replace("%server%", json.get("serverName").getAsString())
								.replace("%name%", json.get("playerName").getAsString())
								.replace("%roleColor%", "white")
								.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

						SERVER.getPlayerManager().getPlayerList().forEach(
								player -> player.sendMessage(text, false));
					} else {
						if (json.get("isText").getAsBoolean()) {
							LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", Objects.requireNonNull(Text.Serializer.fromJson(json.get("message").getAsString())).getString()));

							Text text = Text.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", ""));

							//#if MC <= 11802
							//$$ SERVER.getPlayerManager().getPlayerList().forEach(
							//$$ 		player -> player.sendMessage(new LiteralText("")
							//$$ 				.append(text)
							//$$ 				.append(Text.Serializer.fromJson(json.get("message").getAsString())), false));
							//#else
							List<Text> textList = new ArrayList<>();
							textList.add(text);
							textList.add(Text.Serializer.fromJson(json.get("message").getAsString()));

							SERVER.getPlayerManager().getPlayerList().forEach(
									player -> player.sendMessage(Texts.join(textList, Text.of("")), false));
							//#endif
						} else {
							LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", json.get("message").getAsString()));

							Text text = Text.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

							SERVER.getPlayerManager().getPlayerList().forEach(
									player -> player.sendMessage(text, false));
						}
					}
				} catch (Exception e) {
					LOGGER.info("[MultiServer] Disconnected from the server");
					break;
				}
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}