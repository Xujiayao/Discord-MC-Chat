package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftPlatformHost;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * The entry point for the Fabric environment.
 * <p>
 * Everything game-related that is not loader-specific lives in the shared {@code minecraft:common}
 * module, so this class only does what is inherently Fabric's business: registering the platform
 * implementation.
 *
 * @author Xujiayao
 */
public final class FabricDMCC implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		DMCC.init(new MinecraftPlatformHost("fabric"));
	}
}
