package top.xujiayao.mcdiscordchat.multiServer.client;

import net.minecraft.text.LiteralText;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;

/**
 * @author Xujiayao
 */
public class ReadThread extends Thread {

	private BufferedReader reader;

	public ReadThread(Socket socket) {
		try {
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				String message = reader.readLine();
				LOGGER.info(message);
				SERVER.getPlayerManager().getPlayerList().forEach(
						player -> {
							try {
								player.sendMessage(new LiteralText(message), false);
							} catch (Exception e) {
								LOGGER.error(ExceptionUtils.getStackTrace(e));
							}
						});
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
				break;
			}
		}
	}
}