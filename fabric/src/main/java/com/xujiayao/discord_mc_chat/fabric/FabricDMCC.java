package com.xujiayao.discord_mc_chat.fabric;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftPlatformHost;
import com.xujiayao.discord_mc_chat.minecraft.mod.ModIntegration;
import com.xujiayao.discord_mc_chat.minecraft.mod.ModIntegrations;
import me.drex.vanish.api.VanishAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * The entry point for the Fabric environment.
 * <p>
 * Everything game-related that is not loader-specific lives in the shared {@code minecraft-common}
 * source directory, which this module compiles together with the core module. This class only does
 * the two things that are inherently Fabric's business: registering the platform implementation and
 * registering the mod integrations whose target mods are present.
 *
 * @author Xujiayao
 */
public final class FabricDMCC implements DedicatedServerModInitializer {

	private static final String PLATFORM_NAME = "fabric";

	@Override
	public void onInitializeServer() {
		registerModIntegrations();
		DMCC.init(new MinecraftPlatformHost(PLATFORM_NAME));
	}

	/**
	 * Registers every Fabric-only mod integration whose target mod is currently loaded.
	 * <p>
	 * This method is the template for adding compatibility with another mod: keep the mod-specific
	 * code in the loader module (never in {@code minecraft-common}), guard the registration with the
	 * loader's own "is this mod loaded" check, and implement {@link ModIntegration}.
	 */
	private static void registerModIntegrations() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("vanish") || loader.isModLoaded("melius-vanish")) {
			ModIntegrations.register(new VanishIntegration());
		}
	}

	/**
	 * Minimal Vanish integration: keeps vanished players out of DMCC's public player lists and counts.
	 * <p>
	 * This is deliberately not a complete Vanish integration; it exists to document the pattern for
	 * future mod-specific integrations.
	 */
	private static final class VanishIntegration implements ModIntegration {

		@Override
		public String id() {
			return "vanish";
		}

		@Override
		public Set<String> modIds() {
			return Set.of("vanish", "melius-vanish");
		}

		@Override
		public boolean isPlayerHidden(ServerPlayer player) {
			return VanishAPI.isVanished(player);
		}
	}
}
