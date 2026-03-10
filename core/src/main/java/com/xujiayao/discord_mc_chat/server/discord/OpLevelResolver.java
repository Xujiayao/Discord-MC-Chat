package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.List;

/**
 * Centralized OP level resolution for Discord users.
 * <p>
 * Resolves OP levels from config mappings with optional per-server granularity.
 * Used by both DiscordEventHandler (for command authorization) and the
 * sync_op_level_to_minecraft feature.
 *
 * @author Xujiayao
 */
public class OpLevelResolver {

	/**
	 * Resolves the top-level OP level for a Discord user (used by standalone/single_server).
	 *
	 * @param member The Discord Member object (null if in DMs).
	 * @param user   The Discord User object.
	 * @return The resolved OP level (-1 to 4).
	 */
	public static int resolve(Member member, User user) {
		return resolveInternal(member, user, null);
	}

	/**
	 * Resolves the OP level for a Discord user targeting a specific DMCC client server.
	 * <p>
	 * In standalone mode, reads the per-server OP from the {@code servers} array in mappings.
	 * Falls back to the top-level {@code op_level} if no server-specific entry exists.
	 *
	 * @param member     The Discord Member object (null if in DMs).
	 * @param user       The Discord User object.
	 * @param serverName The target DMCC client server name.
	 * @return The resolved OP level (-1 to 4).
	 */
	public static int resolveForServer(Member member, User user, String serverName) {
		return resolveInternal(member, user, serverName);
	}

	/**
	 * Internal OP level resolution logic.
	 *
	 * @param member     The Discord Member object (null if in DMs).
	 * @param user       The Discord User object.
	 * @param serverName If non-null, resolve per-server OP from the {@code servers} list.
	 * @return The resolved OP level (-1 to 4).
	 */
	private static int resolveInternal(Member member, User user, String serverName) {
		int maxOp = -1;

		// Check exact user mappings first (highest priority)
		JsonNode userMappings = ConfigManager.getConfigNode("account_linking.op_sync.user_mappings");
		if (userMappings.isArray()) {
			for (JsonNode node : userMappings) {
				if (user.getId().equals(node.path("user").asText()) || user.getName().equals(node.path("user").asText())) {
					maxOp = Math.max(maxOp, resolveOpFromNode(node, serverName));
				}
			}
		}

		// Check role mappings if member exists (in a guild)
		if (member != null) {
			JsonNode roleMappings = ConfigManager.getConfigNode("account_linking.op_sync.role_mappings");
			if (roleMappings.isArray()) {
				for (Role role : member.getRoles()) {
					for (JsonNode node : roleMappings) {
						if (role.getId().equals(node.path("role").asText()) || role.getName().equals(node.path("role").asText())) {
							maxOp = Math.max(maxOp, resolveOpFromNode(node, serverName));
						}
					}
				}
			}
		}

		// Account Linking: if user has linked MC accounts, they get at least OP 0
		List<String> linkedUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(user.getId());
		if (!linkedUuids.isEmpty() && maxOp < 0) {
			maxOp = 0;
		}

		return maxOp;
	}

	/**
	 * Reads the OP level from a mapping node, optionally for a specific server.
	 *
	 * @param node       The mapping node (user_mappings or role_mappings entry).
	 * @param serverName The target server name (null for top-level OP).
	 * @return The OP level from the mapping.
	 */
	private static int resolveOpFromNode(JsonNode node, String serverName) {
		if (serverName != null) {
			JsonNode serversArray = node.path("servers");
			if (serversArray.isArray()) {
				for (JsonNode serverEntry : serversArray) {
					if (serverName.equals(serverEntry.path("server").asText())) {
						return serverEntry.path("op_level").asInt(-1);
					}
				}
			}
		}
		// Top-level op_level (for standalone and single_server)
		return node.path("op_level").asInt(-1);
	}
}
