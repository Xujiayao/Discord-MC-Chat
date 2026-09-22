package com.xujiayao.discord_mc_chat.minecraft;

import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * The Fabric entry point, declared in {@code fabric.mod.json}.
 *
 * @author Xujiayao
 */
public final class FabricDMCC implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		MinecraftModBootstrap.init();
	}
}
