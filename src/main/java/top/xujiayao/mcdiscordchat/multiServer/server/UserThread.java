package top.xujiayao.mcdiscordchat.multiServer.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
public class UserThread extends Thread {

	private final Socket socket;
	private final Server server;
	private PrintWriter writer;
	private BufferedReader reader;

	public UserThread(Socket socket, Server server) {
		this.socket = socket;
		this.server = server;
	}

	@Override
	public void run() {
		LOGGER.info("[MultiServer] A client is connected to the server");

		try {
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

			while (true) {
				String message = reader.readLine();

				if (message.equals("bye")) {
					reader.close();
					socket.close();
					break;
				}

				if (message.startsWith("channelTopicInfo")) {
					MULTI_SERVER.channelTopicInfoList.add(new Gson().fromJson(message.replace("channelTopicInfo", ""), JsonObject.class));
					continue;
				}

				server.broadcast(message, this);
			}
		} catch (SocketException ignored) {
		} catch (IOException e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

		LOGGER.info("[MultiServer] A client has disconnected from the server");

		stopUserThread();
	}

	public void sendMessage(String message) {
		writer.println(message);
	}

	public void stopUserThread() {
		try {
			socket.close();
			reader.close();

			server.users.removeIf(element -> element == this);
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}