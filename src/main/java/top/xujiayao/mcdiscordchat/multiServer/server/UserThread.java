package top.xujiayao.mcdiscordchat.multiServer.server;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static top.xujiayao.mcdiscordchat.Main.LOGGER;

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

				server.broadcast(message, this);
			}

			remove(true);
		} catch (Exception e) {
			remove(true);
		}
	}

	public void sendMessage(String message) {
		writer.println(message);
	}

	public void remove(boolean shouldRemoveList) {
		try {
			socket.close();
			reader.close();

			if (shouldRemoveList) {
				server.removeUser(this);
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}