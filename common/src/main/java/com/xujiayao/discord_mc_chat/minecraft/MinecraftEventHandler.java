package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.CommonDMCC;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

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
			new Thread(CommonDMCC::shutdown, "DMCC-Shutdown").start();
		});

		EventManager.register(MinecraftEvents.CommandRegister.class, event -> {
			LOGGER.info("[DMCC] Registering commands...");
		});

		LOGGER.info("Initialized all Minecraft event handlers");
	}
}
