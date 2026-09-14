package com.xujiayao.discord_mc_chat.minecraft.mod;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * A compatibility extension that teaches DMCC about another Minecraft mod.
 * <p>
 * DMCC core and this shared module stay independent of every third-party mod: they never
 * reference the target mod's classes, both because that mod may not be installed and because
 * the very same sources are compiled by the Fabric module and by the NeoForge module. The
 * mod-specific code therefore lives behind this interface, in the loader module that owns the
 * dependency, and DMCC reaches it through {@link ModIntegrations}.
 * <p>
 * How to add a new integration:
 * <ol>
 * <li>Implement this interface in the loader module that can see the target mod's API
 * ({@code fabric/} or {@code neoforge/}), and keep every call into that API inside the
 * implementation.</li>
 * <li>Guard registration with the loader's own mod-loaded check, for example
 * {@code FabricLoader.getInstance().isModLoaded("vanish")} on Fabric or
 * {@code ModList.get().isLoaded("vanish")} on NeoForge.</li>
 * <li>Register the implementation during platform initialization with
 * {@link ModIntegrations#register(ModIntegration)}, before the Minecraft server starts and
 * before DMCC builds any player list.</li>
 * <li>Never reference the target mod's classes from this shared module or from DMCC core, so
 * that both keep loading when the target mod is absent.</li>
 * </ol>
 *
 * @author Xujiayao
 */
public interface ModIntegration {

	/**
	 * Gets the stable identifier of this integration.
	 *
	 * @return The integration id, e.g. {@code "vanish"}.
	 */
	String id();

	/**
	 * Gets every mod id this integration applies to.
	 * <p>
	 * Several ids are listed when one feature is provided by multiple mods or forks, e.g.
	 * {@code Set.of("vanish", "melius-vanish")}.
	 *
	 * @return The mod ids handled by this integration.
	 */
	Set<String> modIds();

	/**
	 * Checks whether the target mod currently hides the given player from other players.
	 * <p>
	 * This is the only hook DMCC needs today: it is consulted when DMCC reports or renders the
	 * online player list. Integrations that never hide players keep the default implementation.
	 *
	 * @param player The player to check.
	 * @return {@code true} when the target mod hides this player, otherwise {@code false}.
	 */
	default boolean isPlayerHidden(ServerPlayer player) {
		return false;
	}
}
