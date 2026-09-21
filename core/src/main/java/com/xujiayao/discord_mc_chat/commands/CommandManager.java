package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.commands.impl.ConsoleCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.commands.impl.HelpCommand;
import com.xujiayao.discord_mc_chat.commands.impl.InfoCommand;
import com.xujiayao.discord_mc_chat.commands.impl.LinkCommand;
import com.xujiayao.discord_mc_chat.commands.impl.LinksCommand;
import com.xujiayao.discord_mc_chat.commands.impl.LogCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ReloadCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ShutdownCommand;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.commands.impl.UnlinkCommand;
import com.xujiayao.discord_mc_chat.commands.impl.UpdateCommand;
import com.xujiayao.discord_mc_chat.commands.impl.WhitelistCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central registry and dispatcher for DMCC commands.
 */
public final class CommandManager {

	private static final Map<String, Command> COMMANDS = new ConcurrentHashMap<>();
	// Written by initialize()/shutdown() and read by every dispatching thread, so it has to be volatile.
	private static volatile ExecutorService commandExecutor;

	private CommandManager() {
	}

	public static void initialize() {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			commandExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-Command"));
		}

		COMMANDS.clear();

		register(new HelpCommand());
		register(new InfoCommand());
		register(new LogCommand());
		register(new ReloadCommand());
		register(new UpdateCommand());

		switch (ModeManager.getMode()) {
			case "standalone" -> {
				register(new ConsoleCommand());
				register(new ExecuteCommand());
				register(new LinkCommand());
				register(new LinksCommand());
				register(new ShutdownCommand());
				register(new UnlinkCommand());
			}
			case "single_server" -> {
				register(new ConsoleCommand());
				register(new LinkCommand());
				register(new LinksCommand());
				register(new StatsCommand());
				register(new UnlinkCommand());
				register(new WhitelistCommand());
			}
			case "multi_server_client" -> {
				register(new LinkCommand());
				register(new StatsCommand());
				register(new UnlinkCommand());
				register(new WhitelistCommand());
			}
		}
	}

	public static void shutdown() {
		if (commandExecutor != null) {
			commandExecutor.shutdown();
			commandExecutor = null;
		}
	}

	private static void register(Command command) {
		COMMANDS.put(command.name().toLowerCase(), command);
	}

	public static Collection<Command> getCommands() {
		return new ArrayList<>(COMMANDS.values());
	}

	public static void execute(CommandSender sender, String name, String... args) {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			return;
		}

		commandExecutor.submit(() -> executeInternal(sender, name, args));
	}

	/**
	 * Execute a command line and return a CompletableFuture that completes when execution finishes.
	 * Used by the client handler to send responses after command completion.
	 */
	public static CompletableFuture<Void> executeAndWait(CommandSender sender, String name, String... args) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		if (commandExecutor == null || commandExecutor.isShutdown()) {
			future.completeExceptionally(new IllegalStateException("Command executor is not available"));
			return future;
		}

		commandExecutor.submit(() -> {
			try {
				executeInternal(sender, name, args);
				future.complete(null);
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		});

		return future;
	}

	private static void executeInternal(CommandSender sender, String name, String... args) {
		Command command = COMMANDS.get(name);
		if (command == null) {
			sender.reply(I18nManager.getDmccTranslation("terminal.unknown_command", name));
			return;
		}

		// Edge authorization: compare sender's OP level against local config requirement
		int requiredOp = ConfigManager.getInt("command_permission_levels." + name, 4);
		if (sender.getOpLevel() < requiredOp) {
			sender.reply(I18nManager.getDmccTranslation("commands.insufficient_permission"));
			return;
		}

		int expectedArgs = command.args().length;

		// Too few arguments: show the command's own usage
		if (args.length < expectedArgs) {
			sender.reply(I18nManager.getDmccTranslation("commands.invalid_usage", command.usage()));
			return;
		}

		// Too many arguments: reject (except for commands that accept variable args)
		if (args.length > expectedArgs && !command.acceptsExtraArgs()) {
			sender.reply(I18nManager.getDmccTranslation("commands.invalid_usage", command.usage()));
			return;
		}

		try {
			command.execute(sender, args);
		} catch (Exception e) {
			sender.reply(I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage()));
		}
	}

	/**
	 * Resolves a multi-server target specification into the client names to act on.
	 *
	 * @param sender     The sender to report resolution failures to.
	 * @param target     Either {@code all_online_clients} or the name of a configured client.
	 * @param i18nPrefix Translation prefix of the calling command ({@code commands.console} or {@code commands.execute}).
	 * @return The resolved target, or {@code null} when the target is invalid and the sender was already notified.
	 */
	public static ResolvedTarget resolveTarget(CommandSender sender, String target, String i18nPrefix) {
		List<String> allConnected = NetworkManager.getConnectedClientNames();

		if ("all_online_clients".equalsIgnoreCase(target)) {
			if (allConnected.isEmpty()) {
				sender.reply(I18nManager.getDmccTranslation(i18nPrefix + ".no_online_clients"));
				return null;
			}
			return new ResolvedTarget(allConnected, I18nManager.getDmccTranslation(i18nPrefix + ".all_online_clients"));
		}

		if (!isValidTarget(target)) {
			sender.reply(I18nManager.getDmccTranslation(i18nPrefix + ".invalid_target", target, allConnected));
			return null;
		}

		if (!allConnected.contains(target)) {
			sender.reply(I18nManager.getDmccTranslation(i18nPrefix + ".client_offline", target));
			return null;
		}

		return new ResolvedTarget(List.of(target), target);
	}

	/**
	 * Checks whether the given name matches a client configured in {@code multi_server.servers}.
	 *
	 * @param target The client name to look up.
	 * @return true when the name is configured, false otherwise.
	 */
	private static boolean isValidTarget(String target) {
		JsonNode serversNode = ConfigManager.getConfigNode("multi_server.servers");
		if (serversNode.isArray()) {
			for (JsonNode node : serversNode) {
				if (target.equals(node.path("name").asString())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A resolved multi-server target: the client names to act on and the display name reported to the sender.
	 *
	 * @param clientNames Connected client names to send the request to.
	 * @param displayName Name shown to the sender in user-facing messages.
	 */
	public record ResolvedTarget(List<String> clientNames, String displayName) {
	}
}
