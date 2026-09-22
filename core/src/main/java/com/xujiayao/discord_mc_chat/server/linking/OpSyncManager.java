package com.xujiayao.discord_mc_chat.server.linking;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.events.CoreEvents;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.OpSyncPacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.OpLevelResolver;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
public final class OpSyncManager {

	private static ExecutorService syncExecutor;

	private OpSyncManager() {
	}

	/**
	 * Performs a full OP level sync for all linked accounts.
	 * <p>
	 * The actual work is submitted to a dedicated executor thread to avoid
	 * blocking the caller (e.g. Netty handler threads).
	 * <p>
	 * In single_server mode, posts a CoreEvent to the local Minecraft server.
	 * In standalone mode, sends OpSyncPackets to each connected client.
	 */
	public static void syncAll() {
		try {
			getOrCreateExecutor().execute(OpSyncManager::doSyncAll);
		} catch (RejectedExecutionException e) {
			// Swallowing this hid the loss of an OP level update; it happens while shutting down, so the
			// update is dropped but stays traceable
			LOGGER.warn(I18nManager.getDmccTranslation("linking.op_sync.schedule_failed", e.toString()));
		}
	}

	public static void shutdown() {
		synchronized (OpSyncManager.class) {
			if (syncExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(syncExecutor);
				syncExecutor = null;
			}
		}
	}

	private static ExecutorService getOrCreateExecutor() {
		synchronized (OpSyncManager.class) {
			if (syncExecutor == null || syncExecutor.isShutdown()) {
				syncExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-OpSync"));
			}
			return syncExecutor;
		}
	}

	private static void doSyncAll() {
		if (!ConfigManager.getBoolean("account_linking.op_sync.sync_op_level_to_minecraft")) {
			return;
		}

		Map<String, List<LinkedAccountManager.LinkEntry>> allLinks = LinkedAccountManager.getAllLinks();

		switch (ModeManager.getMode()) {
			case "single_server" -> EventManager.post(new CoreEvents.OpSyncEvent(buildOpLevels(allLinks, null)));
			case "standalone" -> {
				// For each connected client, compute per-server OP levels and send
				for (String clientName : NetworkManager.getConnectedClientNames()) {
					NetworkManager.sendPacketToClient(new OpSyncPacket(buildOpLevels(allLinks, clientName)), clientName);
				}
			}
			// getMode() also returns an empty string before the mode is loaded, and an unrecognized mode
			// would otherwise skip the sync without leaving any trace
			default -> LOGGER.warn(I18nManager.getDmccTranslation("linking.op_sync.unsupported_mode", ModeManager.getMode()));
		}
	}

	/**
	 * Computes the OP level of every linked Minecraft account, optionally restricted to one DMCC client.
	 * <p>
	 * A level of {@code -1} means the Discord user could not be resolved and is skipped; any other level, including
	 * {@code 0}, is synchronized, because the receiver applies the map as a full reset.
	 */
	private static Map<String, Integer> buildOpLevels(Map<String, List<LinkedAccountManager.LinkEntry>> allLinks,
	                                                  String serverName) {
		Map<String, Integer> opLevels = new HashMap<>();
		for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
			int opLevel = resolveOpForDiscordUser(entry.getKey(), serverName);
			if (opLevel >= 0) {
				for (LinkedAccountManager.LinkEntry link : entry.getValue()) {
					opLevels.put(link.minecraftUuid(), opLevel);
				}
			}
		}
		return opLevels;
	}

	private static int resolveOpForDiscordUser(String discordId, String serverName) {
		try {
			User user = DiscordManager.retrieveUser(discordId);
			if (user == null) {
				return -1;
			}
			Member member = DiscordManager.retrieveMember(discordId);
			if (serverName != null) {
				return OpLevelResolver.resolveForServer(member, user, serverName);
			} else {
				return OpLevelResolver.resolve(member, user);
			}
		} catch (Exception e) {
			LOGGER.warn(I18nManager.getDmccTranslation("linking.op_sync.resolve_failed", discordId, e.getMessage()));
			return -1;
		}
	}
}
