package com.xujiayao.discord_mc_chat.common.minecraft;

import com.xujiayao.discord_mc_chat.common.DMCC;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Handles Minecraft events posted from the event manager.
 *
 * @author Xujiayao
 */
public class MinecraftEventHandler {

	/**
	 * Initializes the Minecraft event handlers.
	 */
	public static void init() {
		EventManager.register(MinecraftEvents.ServerStopped.class, event -> {
			// Shutdown DMCC when the server is stopped
			new Thread(DMCC::shutdown, "DMCC-Shutdown").start();
		});

		LOGGER.info("Initialized all Minecraft event handlers");
	}
}
