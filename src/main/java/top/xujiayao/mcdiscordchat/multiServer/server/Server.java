package top.xujiayao.mcdiscordchat.multiServer.server;

import org.apache.commons.lang3.exception.ExceptionUtils;

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

	private final Set<String> users = new HashSet<>();
	private final Set<UserThread> userThreads = new HashSet<>();
	private final ServerSocket serverSocket;

	public Server() throws Exception {
		serverSocket = new ServerSocket(CONFIG.multiServer.port);
		LOGGER.info("[MultiServer] Server has been created and is listening on port " + CONFIG.multiServer.port);
	}

	@Override
	public void run() {
		try {
			while (!serverSocket.isClosed()) {
				Socket socket = serverSocket.accept();

				UserThread newUser = new UserThread(socket, this);
				userThreads.add(newUser);
				newUser.start();
			}
		} catch (Exception e) {
			LOGGER.error("Error in the server: " + e.getMessage());
		}
	}

	public void broadcast(String message, UserThread excludeUserThread) {
		for (UserThread userThread : userThreads) {
			if (userThread != excludeUserThread) {
				userThread.sendMessage(message);
			}
		}
	}

	public void addUser(String name) {
		users.add(name);
	}

	public void removeUser(String name, UserThread userThread) {
		users.remove(name);
		userThreads.remove(userThread);

		if (users.isEmpty()) {
			try {
				serverSocket.close();
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}
}