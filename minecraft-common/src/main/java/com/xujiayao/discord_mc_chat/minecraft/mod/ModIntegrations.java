package com.xujiayao.discord_mc_chat.minecraft.mod;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the {@link ModIntegration}s contributed by the loader modules.
 * <p>
 * The list is normally empty: this shared module ships no third-party compatibility of its own,
 * and the NeoForge module has no integrations yet. Only a loader module that can see the target
 * mod's API registers here, and it registers during platform initialization, after its own
 * mod-loaded check succeeded.
 * <p>
 * Registration happens once at startup while lookups happen whenever the online player list is
 * built, so registrations are stored in a {@link CopyOnWriteArrayList}: adding is safe from any
 * thread, and readers never block or observe a partially updated list.
 *
 * @author Xujiayao
 */
public final class ModIntegrations {

	private static final CopyOnWriteArrayList<ModIntegration> INTEGRATIONS = new CopyOnWriteArrayList<>();

	private ModIntegrations() {
	}

	/**
	 * Registers a mod integration.
	 * <p>
	 * {@code null} is ignored, and an integration whose {@link ModIntegration#id()} is already
	 * registered is ignored as well, so a loader module may call this defensively without
	 * checking the registry first.
	 *
	 * @param integration The integration to register.
	 */
	public static void register(ModIntegration integration) {
		if (integration == null) {
			return;
		}

		for (ModIntegration registered : INTEGRATIONS) {
			if (Objects.equals(registered.id(), integration.id())) {
				return;
			}
		}

		INTEGRATIONS.add(integration);
	}

	/**
	 * Gets a snapshot of all registered integrations.
	 *
	 * @return The registered integrations, in registration order.
	 */
	public static List<ModIntegration> all() {
		return List.copyOf(INTEGRATIONS);
	}

	/**
	 * Checks whether any registered integration hides the given player.
	 *
	 * @param player The player to check.
	 * @return {@code true} as soon as one integration reports the player as hidden, otherwise {@code false}.
	 */
	public static boolean isPlayerHidden(ServerPlayer player) {
		for (ModIntegration integration : INTEGRATIONS) {
			if (integration.isPlayerHidden(player)) {
				return true;
			}
		}

		return false;
	}
}
