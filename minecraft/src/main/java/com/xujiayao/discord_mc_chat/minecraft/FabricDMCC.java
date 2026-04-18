package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * The entry point for Fabric environment.
 *
 * @author Xujiayao
 */
public final class FabricDMCC implements DedicatedServerModInitializer {

	public FabricDMCC() {
	}

	/**
	 * Start NeoForge DMCC.
	 */
	@Override
	public void onInitializeServer() {
		DMCC.init();

		// Minecraft commands have to be registered after DMCC is initialized
		// to apply command permission levels from the config
		MinecraftEventHandler.init();
	}
}
