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
import java.util.regex.Pattern;

import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;

/**
 * @author LofiTurtle
 * @author Xujiayao
 */
public class ConsoleLogListener implements Runnable {

	private static final Pattern formatPattern = Pattern.compile("§.");
	private static final Integer MAX_MESSAGE_LENGTH = 1900;
	private boolean readFileHistory;

	public ConsoleLogListener(boolean readFileHistory) {
		this.readFileHistory = readFileHistory;
	}

	@Override
	public void run() {
		CONSOLE_LOG_CHANNEL.sendMessage(Translations.translate("utils.clListener.startListening")).queue();
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

								while (messageBatch.length() + currentLine.length() < MAX_MESSAGE_LENGTH) {
									// create the message batch
									appendLine(messageBatch, currentLine);

									if (newMessageIterator.hasNext()) {
										currentLine = newMessageIterator.next();
									} else {
										finishedSendingMessages = true;
										break;
									}
								}

								if (messageBatch.isEmpty()) {
									// currentLine is somehow larger than char limit, so send in multiple parts
									for (int i = 0; i < currentLine.length(); i += MAX_MESSAGE_LENGTH) {
										appendLine(messageBatch, currentLine.substring(i, Math.min(i + MAX_MESSAGE_LENGTH, currentLine.length())));
										messageBatch.deleteCharAt(messageBatch.lastIndexOf("\n"));
										sendLogChannelMessage(messageBatch.toString());
										messageBatch.delete(0, messageBatch.length());
									}
									finishedSendingMessages = true;
								} else {
									messageBatch.deleteCharAt(messageBatch.lastIndexOf("\n"));
									sendLogChannelMessage(messageBatch.toString());
									messageBatch.delete(0, messageBatch.length());
								}
							}
						}

						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
					return;
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
					// don't re-send all logs when restarting from an error
					readFileHistory = false;
				}
			}
		} finally {
			CONSOLE_LOG_CHANNEL.sendMessage(Translations.translate("utils.clListener.stopListening")).queue();
			LOGGER.info("[ConsoleLog] Closing ConsoleLogListener");
		}
	}

	private void appendLine(StringBuilder messageBatch, String currentLine) {
		currentLine = formatPattern.matcher(currentLine).replaceAll("");
		currentLine = currentLine.replace("`", "");
		messageBatch.append(currentLine.isEmpty() ? currentLine : ('`' + currentLine + '`'));
		messageBatch.append("\n");
	}

	private void sendLogChannelMessage(String message) {
		if (message.isEmpty()) {
			return;
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
