package top.xujiayao.mcdiscordchat.multiServer.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
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

				if (json.get("sendToDiscordOnly").getAsBoolean()) {
					CHANNEL.sendMessage(json.get("message").getAsString()).queue();
					continue;
				}

				StringBuilder consoleMessage = new StringBuilder()
						.append("[").append(json.get("name").getAsString()).append("] ")
						.append(json.get("isChat").getAsBoolean() ? "<" + json.get("playerName").getAsString() + "> " : "")
						.append(json.get("message").getAsString());

				Utils.sendConsoleMessage(consoleMessage);

				LiteralText text = new LiteralText(Formatting.BLUE.toString() + Formatting.BOLD + "[" + json.get("name").getAsString() + "] " + Formatting.RESET
						+ (json.get("isChat").getAsBoolean() ? "<" + json.get("playerName").getAsString() + "> " : "")
						+ Formatting.GRAY + MarkdownParser.parseMarkdown(json.get("message").getAsString()));

				SERVER.getPlayerManager().getPlayerList().forEach(
						player -> {
							try {
								player.sendMessage(text, false);
							} catch (Exception e) {
								LOGGER.error(ExceptionUtils.getStackTrace(e));
							}
						});
			} catch (Exception e) {
				break;
			}
		}
	}
}