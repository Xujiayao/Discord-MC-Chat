package com.xujiayao.mcdiscordchat.multi_server.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xujiayao.mcdiscordchat.utils.MarkdownParser;
import com.xujiayao.mcdiscordchat.utils.Translations;
import com.xujiayao.mcdiscordchat.utils.Utils;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static com.xujiayao.mcdiscordchat.Main.CONFIG;
import static com.xujiayao.mcdiscordchat.Main.JDA;
import static com.xujiayao.mcdiscordchat.Main.LOGGER;
import static com.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static com.xujiayao.mcdiscordchat.Main.SERVER;
import static com.xujiayao.mcdiscordchat.Main.SERVER_STARTED_TIME;

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
						if ("discordInfoCommand".equals(message.get("type").getAsString())) {
							TextChannel channel = JDA.getTextChannelById(message.get("channel").getAsString());
							Objects.requireNonNull(channel).sendMessage("```\n" + Utils.getInfoCommandMessage() + "\n```").queue();
						} else if ("updateChannelTopic".equals(message.get("type").getAsString())) {
							JsonObject channelTopicInfo = new JsonObject();
							channelTopicInfo.addProperty("onlinePlayerCount", SERVER.getPlayerCount());
							channelTopicInfo.addProperty("maxPlayerCount", SERVER.getMaxPlayers());

							Properties properties = new Properties();
							properties.load(new FileInputStream("server.properties"));

							Set<String> uniquePlayers = new HashSet<>();
							FileUtils.listFiles(new File((properties.getProperty("level-name") + "/stats/")), null, false).forEach(file -> uniquePlayers.add(file.getName()));
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

						MutableComponent text = Component.Serializer.fromJson(Translations.translateMessage("message.formattedChatMessage")
								.replace("%server%", json.get("serverName").getAsString())
								.replace("%name%", json.get("playerName").getAsString())
								.replace("%roleColor%", "white")
								.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

						SERVER.getPlayerList().getPlayers().forEach(
								player -> player.displayClientMessage(text, false));
					} else {
						if (json.get("isText").getAsBoolean()) {
							LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", Objects.requireNonNull(Component.Serializer.fromJson(json.get("message").getAsString())).getString()));

							MutableComponent text = Component.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", ""));

							Objects.requireNonNull(text).append(Component.Serializer.fromJson(json.get("message").getAsString()));

							SERVER.getPlayerList().getPlayers().forEach(
									player -> player.displayClientMessage(text, false));
						} else {
							LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", json.get("message").getAsString()));

							MutableComponent text = Component.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
									.replace("%server%", json.get("serverName").getAsString())
									.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

							SERVER.getPlayerList().getPlayers().forEach(
									player -> player.displayClientMessage(text, false));
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