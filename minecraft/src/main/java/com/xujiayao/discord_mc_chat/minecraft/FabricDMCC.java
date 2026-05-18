package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

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
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("vanish") || loader.isModLoaded("melius-vanish")) {
			Constants.MOD_VANISH_INSTALLED.set(true);
		}

		DMCC.init();

		// Minecraft commands have to be registered after DMCC is initialized
		// to apply command permission levels from the config
		MinecraftEventHandler.init();
	}
}
