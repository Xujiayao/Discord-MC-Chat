package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.fabricmc.api.ModInitializer;

public class FabricDMCC implements ModInitializer {

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		DMCC.init("Fabric");
	}
}
