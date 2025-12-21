package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.StringUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
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

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> new Thread(r, "DMCC-Command"));;

	/**
	 * Initializes the Command event handlers.
	 */
	public static void init() {
		EventManager.register(CommandEvents.ReloadEvent.class, event -> EXECUTOR.submit(DMCC::reload));

		EventManager.register(CommandEvents.ShutdownEvent.class, event -> EXECUTOR.submit(() -> {
			System.exit(0);
		}));

		LOGGER.info("Initialized all Command event handlers");
	}

	/**
	 * Builds and returns the help message string (plain text without formatting).
	 *
	 * @return The plain help message
	 */
	public static String buildHelpMessage() {
		return StringUtils.format("""
						==================== {} ====================
						/disable  | {}
						/enable   | {}
						/help     | {}
						/reload   | {}
						/shutdown | {}
						""",
				I18nManager.getDmccTranslation("commands.help.help"),
				I18nManager.getDmccTranslation("commands.disable.description"),
				I18nManager.getDmccTranslation("commands.enable.description"),
				I18nManager.getDmccTranslation("commands.help.description"),
				I18nManager.getDmccTranslation("commands.reload.description"),
				I18nManager.getDmccTranslation("commands.shutdown.description"));
	}

	/**
	 * Shuts down the command executor service.
	 */
	public static void shutdown() {
		ExecutorServiceUtils.shutdownAnExecutor(EXECUTOR);
	}
}
