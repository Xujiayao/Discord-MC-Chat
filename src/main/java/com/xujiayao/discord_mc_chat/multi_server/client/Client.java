package com.xujiayao.discord_mc_chat.multi_server.client;

import java.net.Socket;

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Client {

	public WriteThread writeThread;
	public ReadThread readThread;
	public Socket socket;

	public void connect() throws Exception {
		socket = new Socket(CONFIG.multiServer.host, CONFIG.multiServer.port);
		LOGGER.info("[MultiServer] Connected to the server");

		readThread = new ReadThread(socket);
		readThread.start();

		writeThread = new WriteThread(socket);
		writeThread.start();
	}
}
