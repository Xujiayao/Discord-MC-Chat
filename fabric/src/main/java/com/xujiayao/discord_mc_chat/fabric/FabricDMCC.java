package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.fabricmc.api.ModInitializer;

public class FabricDMCC implements ModInitializer {

	@Override
	public void onInitialize() {
		DMCC.init("Fabric");
	}
}
