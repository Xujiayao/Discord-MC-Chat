package top.xujiayao.mcdiscordchat.utils;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;

/**
 * @author LofiTurtle
 * @author Xujiayao
 */
public class ConsoleLogListener implements Runnable {

	private final boolean readFileHistory;

	public ConsoleLogListener(boolean readFileHistory) {
		this.readFileHistory = readFileHistory;
	}

	@Override
	public void run() {
		sendLogChannelMessage(Translations.translate("utils.clListener.startListening"));
		LOGGER.info("[ConsoleLog] Starting new ConsoleLogListener");

		final File file = new File(FabricLoader.getInstance().getGameDir().toString() + "/logs/latest.log");

		try {
			while (true) {
				for (int i = 0; i < 10; i++) {
					// wait for latest.log to exist
					if (file.exists()) {
						LOGGER.info("[ConsoleLog] Listening to new latest.log");
						break;
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						return;
					}
				}

				try (InputStream is = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
					BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

					if (!readFileHistory) {
						// skip to bottom of file
						br.lines().count();
					}

					LocalDate dateLastUpdated = LocalDate.now();

					// if the date changed, exit to get the new latest.log file
					while (dateLastUpdated.equals(LocalDate.now())) {

						List<String> lines = br.lines().toList();
						if (!lines.isEmpty()) {
							// new messages in log file
							ArrayList<String> newMessages = new ArrayList<>();
							for (String line : lines) {
								// br.lines() doesn't always split on "\n"
								newMessages.addAll(new ArrayList<>(Arrays.asList(line.split("\n"))));
							}
							// logs can get long. split into multiple messages if necessary
							StringBuilder messageBatch = new StringBuilder();
							Iterator<String> newMessageIterator = newMessages.iterator();
							String currentLine = newMessageIterator.next();
							boolean finishedSendingMessages = false;
							while (!finishedSendingMessages) {

								while (messageBatch.length() + currentLine.length() < 1900) {
									// create the message batch
									messageBatch.append(currentLine);
									messageBatch.append("\n");
									if (newMessageIterator.hasNext()) {
										currentLine = newMessageIterator.next();
									} else {
										finishedSendingMessages = true;
										break;
									}
								}

								if (messageBatch.isEmpty()) {
									// currentLine is somehow larger than char limit
									messageBatch.append(currentLine);
								}

								messageBatch.deleteCharAt(messageBatch.lastIndexOf("\n"));
								sendLogChannelMessage(messageBatch.toString());
								messageBatch.delete(0, messageBatch.length());
							}
						}

						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
					return;
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}
			}
		} finally {
			sendLogChannelMessage(Translations.translate("utils.clListener.stopListening"));
			LOGGER.info("[ConsoleLog] Closing ConsoleLogListener");
		}
	}

	private void sendLogChannelMessage(String message) {
		if (message.isEmpty()) {
			return;
		} else if (message.length() > 1900) {
			message = message.substring(0, 1900) + "...";
		}

		if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20_000) {
			MINECRAFT_SEND_COUNT = 0;
			MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
		}
		MINECRAFT_SEND_COUNT++;
		if (MINECRAFT_SEND_COUNT <= 20) {
			CONSOLE_LOG_CHANNEL.sendMessage(message).queue();
		}
	}
}
