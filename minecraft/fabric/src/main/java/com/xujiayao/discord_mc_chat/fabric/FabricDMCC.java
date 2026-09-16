package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftPlatformHost;
import com.xujiayao.discord_mc_chat.minecraft.events.PlayerVisibility;
import me.drex.vanish.api.VanishAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The entry point for the Fabric environment.
 * <p>
 * Everything game-related that is not loader-specific lives in the shared {@code minecraft:common}
 * module, so this class only does what is inherently Fabric's business: registering the platform
 * implementation and the compile-only mod compatibility whose target mod may not be installed.
 *
 * @author Xujiayao
 */
public final class FabricDMCC implements DedicatedServerModInitializer {

	/**
	 * Keeps vanished players out of DMCC's public player lists and counts.
	 * <p>
	 * Vanish is a compile-only dependency, so the call into it is guarded by a mod-loaded check. This is
	 * the whole Fabric-side integration; the shared game code only sees the installed check.
	 */
	private static void registerVanish() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("vanish") || loader.isModLoaded("melius-vanish")) {
			PlayerVisibility.setHiddenCheck(VanishAPI::isVanished);
		}
	}

	@Override
	public void onInitializeServer() {
		registerVanish();
		DMCC.init(new MinecraftPlatformHost("fabric"));
	}
}
