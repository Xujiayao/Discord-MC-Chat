package com.xujiayao.discord_mc_chat.common.commands;

import com.xujiayao.discord_mc_chat.common.DMCC;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;

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
			DMCC.reload();
		}, "DMCC-Command").start());

		EventManager.register(CommandEvents.StopEvent.class, event -> new Thread(() -> {
			// TODO different actions for different environments
			System.exit(0);
		}, "DMCC-Command").start());

		LOGGER.info("Initialized all Command event handlers");
	}
}
