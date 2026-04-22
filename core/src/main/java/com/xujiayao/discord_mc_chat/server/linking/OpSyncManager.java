package com.xujiayao.discord_mc_chat.server.linking;

import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.OpSyncPacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.discord.OpLevelResolver;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages the synchronization of OP levels from Discord mappings to Minecraft servers.
 * <p>
 * When {@code sync_op_level_to_minecraft} is enabled, this class computes the desired OP level
 * for every linked Minecraft account based on the Discord user's mapping configuration,
 * then applies a full-reset sync to the Minecraft server(s).
 * <p>
 * All sync operations are queued on a dedicated single-thread executor to avoid
 * blocking Netty IO threads or other callers with JDA's blocking API calls.
 *
 * @author Xujiayao
 */
public final class OpSyncManager {

	private static final ExecutorService SYNC_EXECUTOR =
			Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-OpSync"));

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
		SYNC_EXECUTOR.execute(OpSyncManager::doSyncAll);
	}

	private static void doSyncAll() {
		if (!ConfigManager.getBoolean("account_linking.op_sync.sync_op_level_to_minecraft")) {
			return;
		}

		Map<String, List<LinkedAccountManager.LinkEntry>> allLinks = LinkedAccountManager.getAllLinks();

		switch (ModeManager.getMode()) {
			case "single_server" -> {
				// Compute OP levels for all linked accounts using flat mappings
				Map<String, Integer> opLevels = new HashMap<>();
				for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
					String discordId = entry.getKey();
					int opLevel = resolveOpForDiscordUser(discordId, null);
					if (opLevel > 0) {
						for (LinkedAccountManager.LinkEntry link : entry.getValue()) {
							opLevels.put(link.minecraftUuid(), opLevel);
						}
					}
				}
				EventManager.post(new CoreEvents.OpSyncEvent(opLevels));
			}
			case "standalone" -> {
				// For each connected client, compute per-server OP levels and send
				List<String> clients = NetworkManager.getConnectedClientNames();
				for (String clientName : clients) {
					Map<String, Integer> opLevels = new HashMap<>();
					for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
						String discordId = entry.getKey();
						int opLevel = resolveOpForDiscordUser(discordId, clientName);
						if (opLevel > 0) {
							for (LinkedAccountManager.LinkEntry link : entry.getValue()) {
								opLevels.put(link.minecraftUuid(), opLevel);
							}
						}
					}
					NetworkManager.sendPacketToClient(new OpSyncPacket(opLevels), clientName);
				}
			}
		}
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
