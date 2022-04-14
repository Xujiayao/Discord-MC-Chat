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

	public void sendMessage(boolean special, boolean isChat, String playerName, String message) {
		JsonObject json = new JsonObject();
		json.addProperty("name", CONFIG.multiServer.name);
		json.addProperty("special", special);
		json.addProperty("isChat", isChat);
		json.addProperty("playerName", playerName);
		json.addProperty("message", message);

		client.writeThread.write(json.toString());
	}

//	int onlinePlayerCount = 0;
//	int maxPlayerCount = 0;
//	Set<String> uniquePlayers = new HashSet<>();
//	int onlineServerCount = 0;
//	List<String> onlineServerList = new ArrayList<>();
//	List<Long> serverStartedTime = new ArrayList<>();
//
//				for (JsonObject json : serverInfoList) {
//		onlineServerCount++;
//
//		onlinePlayerCount += json.get("onlinePlayerCount").getAsInt();
//
//		maxPlayerCount += json.get("maxPlayerCount").getAsInt();
//
//		String[] fileList = new Gson().fromJson(json.get("uniquePlayers").getAsJsonArray(), String[].class);
//		uniquePlayers.addAll(List.of(fileList));
//
//		onlineServerList.add(json.get("name").getAsString());
//
//		serverStartedTime.add(Long.parseLong(json.get("serverStartedTime").getAsString()));
//	}
//
//	String topic = TEXTS.onlineChannelTopicForMultiServer()
//			.replace("%onlinePlayerCount%", Integer.toString(onlinePlayerCount))
//			.replace("%maxPlayerCount%", Integer.toString(maxPlayerCount))
//			.replace("%uniquePlayerCount%", Integer.toString(uniquePlayers.size()))
//			.replace("%onlineServerCount%", Integer.toString(onlineServerCount))
//			.replace("%onlineServerList%", String.join(", ", onlineServerList))
//			.replace("%serverStartedTime%", Long.toString(Collections.min(serverStartedTime)))
//			.replace("%lastUpdateTime%", Long.toString(Instant.now().getEpochSecond()));
//
//				CHANNEL.getManager().setTopic(topic).queue();
//
//				if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
//		CONSOLE_LOG_CHANNEL.getManager().setTopic(topic).queue();
//	}

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
