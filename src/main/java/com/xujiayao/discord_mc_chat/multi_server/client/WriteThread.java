package com.xujiayao.discord_mc_chat.multi_server.client;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class WriteThread extends Thread {

	private PrintWriter writer;

	public WriteThread(Socket socket) {
		try {
			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public void write(String text) {
		writer.println(text);
	}
}