package com.xujiayao.discord_mc_chat.multi_server.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Server extends Thread {

	public final Set<UserThread> users = new CopyOnWriteArraySet<>();
	public final ServerSocket serverSocket;

	public Server() throws Exception {
		serverSocket = new ServerSocket(CONFIG.multiServer.port);
		LOGGER.info("[MultiServer] Server has been created and is listening on port " + CONFIG.multiServer.port);
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
				LOGGER.info("[MultiServer] Server has stopped");
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
}