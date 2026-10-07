package com.xujiayao.discord_mc_chat.minecraft.linking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the players that DMCC keeps in place until they link a Discord account.
 * <p>
 * A frozen player has every movement and interaction packet dropped by the mixin on
 * {@code ServerGamePacketListenerImpl}, and is pulled back to the position they were frozen at.
 * Chat and commands are deliberately left alone: a frozen player must still be able to ask for help,
 * run {@code /dmcc link} and read the reply that tells them what to do.
 * <p>
 * Frozen players are also made invulnerable for as long as they are held, so monsters neither pick
 * them as a target nor hurt them. Their game mode is never touched: servers do not all default to
 * survival, and a frozen player must not be moved in or out of a game mode by DMCC.
 * <p>
 * That flag is not private state: {@code Entity} saves it into the player data as the
 * {@code Invulnerable} tag and reads it back on the next login, so a release that is missed (a crash
 * or a save while the player is still held) would leave the player untouchable forever. It is
 * therefore always put back when the player is released or leaves, and cleared once more on join in
 * case an earlier build left it behind.
 * <p>
 * The whole state is in memory and disappears with the server process, which is the point: the
 * decision is taken per join, and a DMCC server that is not reachable never freezes anyone.
 *
 * @author Xujiayao
 */
public final class PlayerFreezeManager {

	// The client has to be pulled back, but once per second is enough and keeps the correction
	// packets from turning into a flood of their own
	private static final long CORRECTION_INTERVAL_MILLIS = 1_000L;

	private static final Map<UUID, Anchor> FROZEN = new ConcurrentHashMap<>();

	private PlayerFreezeManager() {
	}

	/**
	 * Freezes a player at their current position; freezing an already frozen player keeps the original
	 * anchor unless they moved to another dimension, which would make the anchor meaningless.
	 */
	public static void freeze(ServerPlayer player) {
		FROZEN.compute(player.getUUID(), (uuid, existing) -> {
			if (existing != null && existing.level == player.level()) {
				return existing;
			}
			// An anchor that is replaced (another dimension) still remembers what invulnerability
			// looked like before DMCC touched it
			Anchor anchor = new Anchor(player, existing != null ? existing.wasInvulnerable : player.isPermanentlyInvulnerable());
			player.setPermanentlyInvulnerable(true);
			return anchor;
		});
	}

	/**
	 * Releases a player and puts their invulnerability back the way it was before they were frozen.
	 */
	public static void unfreeze(ServerPlayer player) {
		Anchor anchor = FROZEN.remove(player.getUUID());
		if (anchor != null) {
			player.setPermanentlyInvulnerable(anchor.wasInvulnerable);
		}
	}

	/**
	 * Clears what an earlier login may have left behind. Nobody can be held at the moment they join,
	 * and the invulnerable flag travels with the player data, so this is the one place where the
	 * leftovers of a freeze that never got released can be undone.
	 */
	public static void repairOnJoin(ServerPlayer player) {
		FROZEN.remove(player.getUUID());
		if (player.isPermanentlyInvulnerable()) {
			player.setPermanentlyInvulnerable(false);
		}
	}

	public static boolean isFrozen(ServerPlayer player) {
		return FROZEN.containsKey(player.getUUID());
	}

	public static void clear() {
		FROZEN.clear();
	}

	/**
	 * Pulls a frozen player back to the anchor. Called from the movement handler, because dropping the
	 * packet also drops the vanilla correction that would otherwise tell the client to stop moving.
	 */
	public static void correctIfNeeded(ServerPlayer player) {
		Anchor anchor = FROZEN.get(player.getUUID());
		if (anchor == null || anchor.level != player.level()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - anchor.lastCorrectionAt < CORRECTION_INTERVAL_MILLIS) {
			return;
		}
		anchor.lastCorrectionAt = now;
		player.connection.teleport(anchor.x, anchor.y, anchor.z, anchor.yRot, anchor.xRot);
	}

	/**
	 * The position a player is held at. Guarded by the server thread that handles the packets.
	 */
	private static final class Anchor {
		private final ServerLevel level;
		private final double x;
		private final double y;
		private final double z;
		private final float yRot;
		private final float xRot;
		private final boolean wasInvulnerable;
		private long lastCorrectionAt;

		private Anchor(ServerPlayer player, boolean wasInvulnerable) {
			this.level = player.level();
			this.x = player.getX();
			this.y = player.getY();
			this.z = player.getZ();
			this.yRot = player.getYRot();
			this.xRot = player.getXRot();
			this.wasInvulnerable = wasInvulnerable;
		}
	}
}
