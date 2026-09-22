package com.xujiayao.discord_mc_chat.minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The NeoForge entry point, declared in {@code META-INF/neoforge.mods.toml}.
 *
 * @author Xujiayao
 */
@Mod(value = NeoForgeDMCC.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeDMCC {

	public static final String MOD_ID = "discord_mc_chat";

	public NeoForgeDMCC() {
		MinecraftModBootstrap.init();
	}
}
