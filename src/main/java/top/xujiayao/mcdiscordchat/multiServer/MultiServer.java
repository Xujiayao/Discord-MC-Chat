package top.xujiayao.mcdiscordchat.multiServer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.multiServer.client.Client;
import top.xujiayao.mcdiscordchat.multiServer.server.Server;

import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class MultiServer extends Thread {

	public Client client;

	@Override
	public void run() {
		try {
			client = new Client();
			client.connect();
		} catch (Exception e) {
			try {
				new Server().start();

				Thread.sleep(500);

				client = new Client();
				client.connect();
			} catch (Exception ex) {
				LOGGER.error(ExceptionUtils.getStackTrace(ex));
			}
		}
	}

	public void sendMessage(String message) {
		client.getWriteThread().write(message);
	}
}
