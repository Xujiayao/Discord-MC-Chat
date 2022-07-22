package top.xujiayao.mcdiscordchat.utils;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static top.xujiayao.mcdiscordchat.Main.*;

public class ConsoleLogManager implements Runnable {

    @Override
    public void run() {
        if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
            if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20_000) {
                MINECRAFT_SEND_COUNT = 0;
                MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
            }
        }
        MINECRAFT_SEND_COUNT++;
        if (MINECRAFT_SEND_COUNT <= 20) {
            CONSOLE_LOG_CHANNEL.sendMessage("**Starting new console log**").queue();
        }

        final File file = new File(FabricLoader.getInstance().getGameDir().toString() + "\\logs\\latest.log");

        long byteOffset = 0;
        while (true) {

            long fileLength = file.length();
            if (fileLength == byteOffset) {
                // no changes
                continue;
            } else if (fileLength > byteOffset) {
                try (InputStream is = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    // messages were added to latest.log
                    br.skip(byteOffset);
                    List<String> lines = br.lines().toList();
                    ArrayList<String> newMessages = new ArrayList<>();
                    for (String line : lines) {
                        // br.lines() doesn't always split on "\n"
                        newMessages.addAll(new ArrayList<>(Arrays.asList(line.split("\n"))));
                    }

                    if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
                        if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20_000) {
                            MINECRAFT_SEND_COUNT = 0;
                            MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
                        }
                    }
                    MINECRAFT_SEND_COUNT++;
                    if (MINECRAFT_SEND_COUNT <= 20) {
                        // logs can get long. split into multiple messages if necessary
                        StringBuilder messageBatch = new StringBuilder();
                        Iterator<String> newMessageIterator = newMessages.iterator();
                        while (newMessageIterator.hasNext()) {
                            messageBatch.append(newMessageIterator.next());
                            messageBatch.append("\n");
                            if (messageBatch.length() > 1500 || !newMessageIterator.hasNext()) {
                                messageBatch.deleteCharAt(messageBatch.lastIndexOf("\n"));
                                CONSOLE_LOG_CHANNEL.sendMessage(messageBatch).queue();
                                messageBatch.delete(0, messageBatch.length());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.info("Closing ConsoleLogManager");
                }
            } else {
                // latest.log somehow got shorter, likely from manually deleting some contents.
                LOGGER.warn("latest.log shrank unexpectedly. Some messages may have been missed.");
            }

            byteOffset = fileLength;

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

//        final Path path = file.toPath().getParent();
//        long byteOffset = 0;
//        try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
//            // watch for changes to files in /logs directory
//            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
//            while (true) {
//                final WatchKey wk = watchService.take();
//                for (WatchEvent<?> event : wk.pollEvents()) {
//                    final Path changed = (Path) event.context();
//                    if (changed.endsWith("latest.log")) {
//                        // there was a change, and it was latest.log
//                        BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));
//                        long bytesSkipped = br.skip(byteOffset);
//                        if (bytesSkipped != byteOffset) {
//                            // latest.log somehow got shorter, likely from manually deleting something.
//                            LOGGER.warn("latest.log shrank unexpectedly. Some messages may have been missed.");
//                        } else {
//                            String newMessages = br.lines().collect(Collectors.joining(System.lineSeparator()));
//                            if (newMessages.length() > 0) {
//                                // skip file changes that don't add new messages
//                                if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
//                                    if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20_000) {
//                                        MINECRAFT_SEND_COUNT = 0;
//                                        MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
//                                    }
//                                }
//                                MINECRAFT_SEND_COUNT++;
//                                if (MINECRAFT_SEND_COUNT <= 20) {
//                                    CONSOLE_LOG_CHANNEL.sendMessage(newMessages).queue();
//                                }
//                            }
//                        }
//                        byteOffset = file.length();
//                    }
//                }
//                boolean valid = wk.reset();
//                if (!valid) {
//                    CONSOLE_LOG_CHANNEL.sendMessage("**Error getting console messages! Failed to locate `latest.log` file**").queue();
//                    LOGGER.error("Error getting console messages! Failed to locate latest.log file");
//                }
//                Thread.sleep(100);
//            }
//        }
//        catch (IOException | InterruptedException e) {
//            throw new RuntimeException(e);
//        }
}

