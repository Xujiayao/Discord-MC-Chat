package top.xujiayao.mcdiscordchat.multiServer;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.multiServer.client.Client;
import top.xujiayao.mcdiscordchat.multiServer.server.Server;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class MultiServer extends Thread {

	public Server server;
	public Client client;

	@Override
	public void run() {
		try {
			client = new Client();
			client.connect();
		} catch (Exception e) {
			try {
				server = new Server();
				server.start();

				Thread.sleep(500);

				client = new Client();
				client.connect();
			} catch (Exception ex) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}

	public void sendMessage(boolean sendToDiscordOnly, boolean isChat, String playerName, String message) {
		JsonObject json = new JsonObject();
		json.addProperty("name", CONFIG.multiServer.name);
		json.addProperty("sendToDiscordOnly", sendToDiscordOnly);
		json.addProperty("isChat", isChat);
		json.addProperty("playerName", playerName);
		json.addProperty("message", message);

		client.writeThread.write(json.toString());
	}

	public void bye() {
		client.writeThread.write("bye");
	}

	public void stopMultiServer() {
		try {
			client.socket.close();

			if (server != null) {
				server.users.forEach(userThread -> userThread.remove(false));
				server.serverSocket.close();
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}
