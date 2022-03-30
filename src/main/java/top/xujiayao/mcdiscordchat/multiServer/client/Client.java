package top.xujiayao.mcdiscordchat.multiServer.client;

import java.net.Socket;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

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