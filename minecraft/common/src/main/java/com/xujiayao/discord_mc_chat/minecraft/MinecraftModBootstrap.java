package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;

/**
 * Platform-independent mod entry point shared by the Fabric and NeoForge entry points.
 *
 * @author Xujiayao
 */
public final class MinecraftModBootstrap {

	private MinecraftModBootstrap() {
	}

	/**
	 * Initializes DMCC and registers the Minecraft event handlers.
	 */
	public static void init() {
		DMCC.init();

		// Minecraft commands have to be registered after DMCC is initialized
		// to apply command permission levels from the config
		MinecraftEventHandler.init();
	}
}
