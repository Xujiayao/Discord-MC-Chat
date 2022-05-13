package top.xujiayao.mcdiscordchat.multiServer.server;

import top.xujiayao.mcdiscordchat.utils.Utils;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Server extends Thread {

	public final Set<UserThread> users = new HashSet<>();
	public final ServerSocket serverSocket;

	public Server() throws Exception {
		serverSocket = new ServerSocket(CONFIG.multiServer.port);
		LOGGER.info("[MultiServer] Server has been created and is listening on port " + CONFIG.multiServer.port);
		if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
			Utils.sendConsoleMessage("[MultiServer] Server has been created and is listening on port " + CONFIG.multiServer.port);
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				Socket socket = serverSocket.accept();

				UserThread newUser = new UserThread(socket, this);
				users.add(newUser);
				newUser.start();
			} catch (Exception e) {
				break;
			}
		}
	}

	public void broadcast(String message) {
		users.forEach(userThread -> userThread.sendMessage(message));
	}

	public void broadcast(String message, UserThread excludeUserThread) {
		users.forEach(userThread -> {
			if (userThread != excludeUserThread) {
				userThread.sendMessage(message);
			}
		});
	}

	public void removeUser(UserThread userThread) {
		users.remove(userThread);
	}
}