package com.xujiayao.discord_mc_chat.neoforge;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftPlatformHost;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The entry point for the NeoForge environment.
 * <p>
 * Mirrors the Fabric entry point: it registers the platform implementation. DMCC is a server-side mod,
 * so it is only loaded on a dedicated server.
 *
 * @author Xujiayao
 */
@Mod(value = NeoForgeDMCC.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeDMCC {

	/**
	 * The mod id, which has to match the entry in {@code META-INF/neoforge.mods.toml}.
	 */
	public static final String MOD_ID = "discord_mc_chat";

	/**
	 * Creates the NeoForge entry point.
	 *
	 * @param modEventBus  The mod event bus, injected by FML.
	 * @param modContainer This mod's container, injected by FML.
	 */
	public NeoForgeDMCC(IEventBus modEventBus, ModContainer modContainer) {
		DMCC.init(new MinecraftPlatformHost("neoforge"));
	}
}
