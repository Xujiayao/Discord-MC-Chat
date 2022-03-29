package top.xujiayao.mcdiscordchat.multiServer.client;

import java.net.Socket;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Client {

	private WriteThread writeThread;

	public void connect() throws Exception {
		Socket socket = new Socket(CONFIG.multiServer.host, CONFIG.multiServer.port);
		LOGGER.info("[MultiServer] Connected to the server");

		new ReadThread(socket).start();
		writeThread = new WriteThread(socket);
		writeThread.start();
	}

	public WriteThread getWriteThread() {
		return writeThread;
	}
}