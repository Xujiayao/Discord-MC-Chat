package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * @author Xujiayao
 */
public class FabricDMCC implements ModInitializer {

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer("discord_mc_chat").orElseThrow().getMetadata().getVersion().getFriendlyString();
		DMCC.init("Fabric", version);
	}
}
