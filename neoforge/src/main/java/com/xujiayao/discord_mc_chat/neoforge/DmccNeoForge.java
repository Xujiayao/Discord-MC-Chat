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
 * Mirrors the Fabric entry point: it registers the platform implementation and, in the future,
 * NeoForge-only mod integrations (see {@code com.xujiayao.discord_mc_chat.minecraft.mod.ModIntegration}).
 * DMCC is a server-side mod, so it is only loaded on a dedicated server.
 *
 * @author Xujiayao
 */
@Mod(value = DmccNeoForge.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class DmccNeoForge {

	/**
	 * The mod id, which has to match the entry in {@code META-INF/neoforge.mods.toml}.
	 */
	public static final String MOD_ID = "discord_mc_chat";

	private static final String PLATFORM_NAME = "neoforge";

	/**
	 * Creates the NeoForge entry point.
	 *
	 * @param modEventBus  The mod event bus, injected by FML.
	 * @param modContainer This mod's container, injected by FML.
	 */
	public DmccNeoForge(IEventBus modEventBus, ModContainer modContainer) {
		// No NeoForge mod integrations exist yet. Future ones are registered here, exactly like
		// FabricDMCC registers its own, and implement ModIntegration in this module.
		DMCC.init(new MinecraftPlatformHost(PLATFORM_NAME));
	}
}
