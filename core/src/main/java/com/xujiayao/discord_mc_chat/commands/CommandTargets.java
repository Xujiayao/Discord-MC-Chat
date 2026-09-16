package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the {@code <at>} target of the commands that fan out to connected clients.
 * <p>
 * {@code /console} and {@code /execute} validate and expand targets identically; only the translation
 * keys differ, so both go through this helper with their own key prefix.
 *
 * @author Xujiayao
 */
public final class CommandTargets {

	/**
	 * The pseudo target that expands to every connected client.
	 */
	public static final String ALL_ONLINE_CLIENTS = "all_online_clients";

	private CommandTargets() {
	}

	/**
	 * The resolved target set.
	 *
	 * @param servers     Client names to send to, never empty.
	 * @param displayName Human readable target description used in progress messages.
	 */
	public record Target(List<String> servers, String displayName) {
	}

	/**
	 * @param serverName Candidate client name.
	 * @return Whether the name is configured under {@code multi_server.servers}.
	 */
	public static boolean isConfiguredServer(String serverName) {
		return findServerConfig(serverName) != null;
	}

	/**
	 * Resolves a target argument, replying with the matching error when it cannot be used.
	 *
	 * @param sender    Command sender that receives any error message.
	 * @param target    Raw target argument.
	 * @param keyPrefix Translation key prefix, e.g. {@code commands.console}.
	 * @return The resolved target, or null when the caller should stop.
	 */
	public static Target resolve(CommandSender sender, String target, String keyPrefix) {
		List<String> connected = NetworkManager.getConnectedClientNames();

		if (ALL_ONLINE_CLIENTS.equalsIgnoreCase(target)) {
			if (connected.isEmpty()) {
				sender.reply(I18nManager.getDmccTranslation(keyPrefix + ".no_online_clients"));
				return null;
			}
			return new Target(new ArrayList<>(connected), I18nManager.getDmccTranslation(keyPrefix + ".all_online_clients"));
		}

		if (!isConfiguredServer(target)) {
			sender.reply(I18nManager.getDmccTranslation(keyPrefix + ".invalid_target", target, connected));
			return null;
		}
		if (!connected.contains(target)) {
			sender.reply(I18nManager.getDmccTranslation(keyPrefix + ".client_offline", target));
			return null;
		}
		return new Target(List.of(target), target);
	}

	/**
	 * @param serverName Candidate client name.
	 * @return The entry under {@code multi_server.servers} with this name, or null when there is none.
	 */
	public static JsonNode findServerConfig(String serverName) {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (!serversNode.isArray()) {
			return null;
		}
		for (JsonNode node : serversNode) {
			if (serverName.equals(node.path("name").asString())) {
				return node;
			}
		}
		return null;
	}
}
