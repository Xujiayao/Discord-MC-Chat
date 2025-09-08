package com.xujiayao.discord_mc_chat.common.minecraft;

import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Handles Minecraft events dispatched from the event manager.
 *
 * @author Xujiayao
 */
public class MinecraftEventHandler {

	/**
	 * Initializes the Minecraft event listeners.
	 */
	public static void init() {
		LOGGER.info("Initializing Minecraft event listeners...");

		EventManager.register(MinecraftEvents.PlayerJoin.class, event -> {
			LOGGER.info("[DMCC] Player {} joined the game", event.serverPlayer().getDisplayName());
		});

		LOGGER.info("Minecraft event listeners initialized");
	}
}
