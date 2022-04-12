package top.xujiayao.mcdiscordchat.multiServer.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.text.Text;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
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

				if (json.get("special").getAsBoolean() && json.get("message").getAsString().equals("info")) {
					CHANNEL.sendMessage(Utils.getInfoCommandMessage()).queue();
					continue;
				}

				if (json.get("isChat").getAsBoolean()) {
					Utils.sendConsoleMessage(TEXTS.unformattedChatMessage()
							.replace("%server%", json.get("name").getAsString())
							.replace("%name%", json.get("playerName").getAsString())
							.replace("%message%", json.get("message").getAsString()));

					Text text = Text.Serializer.fromJson(TEXTS.formattedChatMessage()
							.replace("%server%", json.get("name").getAsString())
							.replace("%name%", json.get("playerName").getAsString())
							.replace("%roleColor%", "white")
							.replace("%message%", MarkdownParser.parseMarkdown(json.get("message").getAsString())));

					SERVER.getPlayerManager().getPlayerList().forEach(
							player -> player.sendMessage(text, false));
				} else {
					Utils.sendConsoleMessage(TEXTS.unformattedOtherMessage()
							.replace("%server%", json.get("name").getAsString())
							.replace("%message%", json.get("message").getAsString()));

					Text text = Text.Serializer.fromJson(TEXTS.formattedOtherMessage()
							.replace("%server%", json.get("name").getAsString())
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