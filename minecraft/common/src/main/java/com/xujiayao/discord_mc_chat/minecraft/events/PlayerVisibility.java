package com.xujiayao.discord_mc_chat.minecraft.events;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Lets a loader module teach DMCC that another mod is hiding a player from public player lists.
 * <p>
 * DMCC's shared game code must not reference a third-party mod's classes: that mod may not be installed,
 * and the same source is compiled by both the Fabric and the NeoForge module. The loader module that owns
 * the compile-only dependency therefore installs a check here during platform initialization, for example
 * {@code PlayerVisibility.setHiddenCheck(VanishAPI::isVanished)}.
 *
 * @author Xujiayao
 */
public final class PlayerVisibility {

	/**
	 * Installed once during platform initialization, before the Minecraft server starts.
	 */
	private static volatile Predicate<ServerPlayer> hiddenCheck;

	private PlayerVisibility() {
	}

	/**
	 * Installs the check used to decide whether a mod hides a player.
	 *
	 * @param check Returns true when the player must stay out of public player lists, or null to clear it.
	 */
	public static void setHiddenCheck(Predicate<ServerPlayer> check) {
		hiddenCheck = check;
	}

	public static boolean isPlayerHidden(ServerPlayer player) {
		Predicate<ServerPlayer> check = hiddenCheck;
		return check != null && check.test(player);
	}
}
