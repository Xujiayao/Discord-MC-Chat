package com.xujiayao.discord_mc_chat.common.commands;

import com.xujiayao.discord_mc_chat.common.DMCC;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.common.utils.i18n.I18nManager;

import static com.xujiayao.discord_mc_chat.common.DMCC.IS_MINECRAFT_ENV;
import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Handles Command events posted from the event manager.
 *
 * @author Xujiayao
 */
public class CommandEventHandler {

	/**
	 * Initializes the Command event handlers.
	 */
	public static void init() {
		EventManager.register(CommandEvents.ReloadEvent.class, event -> new Thread(() -> {
			LOGGER.info(I18nManager.getDmccTranslation("commands.reload.reloading"));
			DMCC.reload();
		}, "DMCC-Command").start());

		EventManager.register(CommandEvents.StopEvent.class, event -> new Thread(() -> {
			LOGGER.info(I18nManager.getDmccTranslation("commands.stop.stopping"));

			if (IS_MINECRAFT_ENV) {
				LOGGER.info("Run \"/dmcc start\" to start DMCC again");
				DMCC.shutdown();
			} else {
				System.exit(0);
			}
		}, "DMCC-Command").start());

		LOGGER.info("Initialized all Command event handlers");
	}
}
