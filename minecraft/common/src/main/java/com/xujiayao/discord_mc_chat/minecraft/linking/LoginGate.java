package com.xujiayao.discord_mc_chat.minecraft.linking;

import com.mojang.authlib.GameProfile;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.events.CoreEvents;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.RequestPacket;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.NameAndId;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Keeps an unlinked player out of the server before they ever join it.
 * <p>
 * With {@code unlinked_player_action: kick} this follows the vanilla whitelist model: the connection
 * is refused while it is still logging in, so the player never enters the world, never shows up in
 * the player list, and never makes a join or quit message reach the Discord channel. The only trace
 * is the disconnect line the server writes to its console and log, and the reason the player sees
 * carries the verification code they need to link.
 * <p>
 * The login is parked, not blocked: {@code verifyLoginAndFinishConnectionSetup} is cancelled while
 * Discord has not answered yet, which leaves the login state untouched, and vanilla asks that method
 * again on every tick. The answer is therefore picked up one tick after it arrives, without any
 * polling thread here.
 * <p>
 * Every failure path lets the player in. A DMCC server that cannot be reached, or that does not
 * answer within {@link #TIMEOUT_NANOS}, must never be able to keep anyone out, for the same reason a
 * freeze must never be able to lock a server.
 *
 * @author Xujiayao
 */
public final class LoginGate {

	// A healthy DMCC channel answers a link check in milliseconds; anything slower is a channel that
	// is still being set up or has already gone away, and holding the player longer would only turn
	// into a stalled "Logging in..." screen
	private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private LoginGate() {
	}

	/**
	 * Decides what happens to a login vanilla is about to let through.
	 *
	 * @param server     The server the login belongs to
	 * @param connection The connection of the login
	 * @param listener   The login handler, needed to send the refusal
	 * @param profile    The authenticated profile
	 * @return true when the login has to stay parked, false when vanilla should continue
	 */
	public static boolean shouldHold(MinecraftServer server, Connection connection, ServerLoginPacketListenerImpl listener, GameProfile profile) {
		if (!Constants.REQUIRE_LINKING_ENABLED.get() || !"kick".equals(Constants.UNLINKED_PLAYER_ACTION.get())) {
			return false;
		}

		// The connection is already gone, either because it was refused here a moment ago or because
		// the client left: there is nobody left to answer, so park until the listener is dropped
		if (!connection.isConnected()) {
			return true;
		}

		// Bans, the whitelist and a full server are vanilla's own reasons to refuse a login. They are
		// decided first so the player is told what an unmodded server would tell them
		if (server.getPlayerList().canPlayerLogin(connection.getRemoteAddress(), new NameAndId(profile)) != null) {
			return false;
		}

		// A login that was abandoned before it could be answered would otherwise stay here forever
		PENDING.values().removeIf(pending -> !pending.connection.isConnected());

		Pending pending = PENDING.get(profile.id());
		if (pending == null) {
			if (NetworkManager.getClient() == null) {
				// Fail open: without a DMCC channel the link state cannot be known, and guessing would
				// lock every unlinked player out of a server whose DMCC is simply not running
				return false;
			}

			PENDING.put(profile.id(), new Pending(connection));
			NetworkManager.sendPacketToServer(new RequestPacket(profile.id().toString(), profile.name(), true));
			return true;
		}

		if (pending.denied) {
			return true;
		}

		Boolean linked = pending.linked;
		if (linked == null) {
			if (System.nanoTime() - pending.startedAt > TIMEOUT_NANOS) {
				PENDING.remove(profile.id());
				return false;
			}
			return true;
		}

		if (linked) {
			PENDING.remove(profile.id());
			return false;
		}

		// The entry stays until the connection closes, so the ticks that follow cannot start the
		// exchange over again
		pending.denied = true;
		listener.disconnect(Component.literal(
				I18nManager.getDmccTranslation("linking.require_linking.kick", pending.code == null ? "" : pending.code)));
		return true;
	}

	/**
	 * Hands an answer from Discord to the login it belongs to.
	 *
	 * @param event The link check answer
	 * @return true when the answer was consumed here, because the player has not joined yet
	 */
	public static boolean onResponse(CoreEvents.LinkCodeResponseEvent event) {
		Pending pending = PENDING.get(UUID.fromString(event.playerUuid()));
		if (pending == null) {
			return false;
		}

		pending.code = event.code();
		pending.linked = event.alreadyLinked();
		return true;
	}

	/**
	 * A login that is waiting for its answer. Written by the network thread, read by the server
	 * thread, hence the volatile fields.
	 */
	private static final class Pending {
		private final Connection connection;
		private final long startedAt = System.nanoTime();
		private volatile Boolean linked;
		private volatile String code;
		private volatile boolean denied;

		private Pending(Connection connection) {
			this.connection = connection;
		}
	}
}
