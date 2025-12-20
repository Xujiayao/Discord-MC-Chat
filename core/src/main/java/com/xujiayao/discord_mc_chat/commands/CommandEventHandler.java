package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles Command events posted from the event manager.
 *
 * @author Xujiayao
 */
public class CommandEventHandler {

	private static ExecutorService commandExecutor;

	/**
	 * Initializes the Command event handlers.
	 */
	public static void init() {
		commandExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Command"));

		EventManager.register(CommandEvents.ReloadEvent.class, event -> commandExecutor.submit(() -> {
			LOGGER.info(I18nManager.getDmccTranslation("commands.reload.reloading"));
			DMCC.reload();
		}));

//		EventManager.register(CommandEvents.ShutdownEvent.class, event -> commandExecutor.submit(() -> {
//			LOGGER.info(I18nManager.getDmccTranslation("commands.shutdown.shutting_down"));
//
//			new Thread(() -> {
//				if (IS_MINECRAFT_ENV) {
//					LOGGER.info("Run \"/dmcc enable\" to start DMCC again");
//					DMCC.shutdown();
//				} else {
//					// This will trigger the shutdown hook in StandaloneDMCC
//					System.exit(0);
//				}
//			}, "DMCC-Shutdown").start();
//		}));

		LOGGER.info("Initialized all Command event handlers");
	}

	/**
	 * Shuts down the command executor service.
	 */
	public static void shutdown() {
		if (commandExecutor != null && !commandExecutor.isShutdown()) {
			commandExecutor.shutdown();
			try {
				if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					commandExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				commandExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
}
