package top.xujiayao.mcdiscordchat.multiServer.server;

import net.minecraft.util.Formatting;

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
	private String name;

	public UserThread(Socket socket, Server server) {
		this.socket = socket;
		this.server = server;
	}

	@Override
	public void run() {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

			name = reader.readLine();
			server.addUser(name);

			while (!socket.isInputShutdown()) {
				String message = Formatting.BLUE.toString() + Formatting.BOLD + "[" + name + "] " + Formatting.RESET + reader.readLine();
				server.broadcast(message, this);
			}

			server.removeUser(name, this);
			server.broadcast(name + " has left.", this);

			socket.close();
		} catch (Exception e) {
			try {
				server.removeUser(name, this);
				server.broadcast(name + " has left.", this);

				socket.close();
			} catch (Exception ex) {
				LOGGER.error("Error: " + ex.getMessage());
			}
		}
	}

	public void sendMessage(String message) {
		writer.println(message);
	}
}