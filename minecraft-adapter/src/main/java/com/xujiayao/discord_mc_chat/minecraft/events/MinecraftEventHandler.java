package com.xujiayao.discord_mc_chat.minecraft.events;

import com.xujiayao.discord_mc_chat.DMCC;
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
			Thread shutdownThread = new Thread(DMCC::shutdown, "DMCC-Shutdown");
			shutdownThread.start();

			// Wait for the shutdown thread to finish
			try {
				shutdownThread.join();
			} catch (InterruptedException e) {
				LOGGER.error("Error while waiting for DMCC shutdown thread to finish", e);
				Thread.currentThread().interrupt();
			}
		});

		EventManager.register(MinecraftEvents.CommandRegister.class, event -> {
			LOGGER.info("[DMCC] Registering commands...");
		});

		LOGGER.info("Initialized all Minecraft event handlers");
	}
}
