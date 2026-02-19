package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.commands.impl.ExecuteCommand;
import com.xujiayao.discord_mc_chat.commands.impl.HelpCommand;
import com.xujiayao.discord_mc_chat.commands.impl.InfoCommand;
import com.xujiayao.discord_mc_chat.commands.impl.LogCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ReloadCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ShutdownCommand;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central registry and dispatcher for DMCC commands.
 *
 * @author Xujiayao
 */
public class CommandManager {

	private static final Map<String, Command> COMMANDS = new ConcurrentHashMap<>();
	private static ExecutorService commandExecutor;

	/**
	 * Initialize and register built-in commands based on the current operating mode.
	 */
	public static void initialize() {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			commandExecutor = Executors.newSingleThreadExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-Command"));
		}

		COMMANDS.clear();

		register(new HelpCommand());
		register(new InfoCommand());
		register(new ReloadCommand());
		register(new LogCommand());

		if ("standalone".equals(ModeManager.getMode())) {
			register(new ExecuteCommand());
			register(new ShutdownCommand());
		}
	}

	/**
	 * Shutdown the command executor.
	 */
	public static void shutdown() {
		if (commandExecutor != null) {
			commandExecutor.shutdown();
			commandExecutor = null;
		}
	}

	/**
	 * Register a command.
	 *
	 * @param command The command to register
	 */
	public static void register(Command command) {
		COMMANDS.put(command.name().toLowerCase(), command);
	}

	/**
	 * Get all registered commands.
	 *
	 * @return A collection of registered commands
	 */
	public static Collection<Command> getCommands() {
		return new ArrayList<>(COMMANDS.values());
	}

	/**
	 * Get all registered command names.
	 *
	 * @return A collection of registered command names
	 */
	public static Collection<String> getCommandNames() {
		return new ArrayList<>(COMMANDS.keySet());
	}

	/**
	 * Execute a command line.
	 *
	 * @param sender The command sender
	 * @param name   The command name
	 * @param args   The command arguments (if any)
	 */
	public static void execute(CommandSender sender, String name, String... args) {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			return;
		}

		commandExecutor.submit(() -> executeInternal(sender, name, args));
	}

	/**
	 * Execute a command line and return a CompletableFuture that completes when execution finishes.
	 * Used by the client handler to send responses after command completion.
	 *
	 * @param sender The command sender
	 * @param name   The command name
	 * @param args   The command arguments (if any)
	 * @return A CompletableFuture that completes when the command finishes
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

	/**
	 * Internal command execution logic.
	 *
	 * @param sender The command sender
	 * @param name   The command name
	 * @param args   The command arguments
	 */
	private static void executeInternal(CommandSender sender, String name, String... args) {
		Command command = COMMANDS.get(name);
		if (command == null) {
			sender.reply(I18nManager.getDmccTranslation("terminal.unknown_command", name));
			return;
		}

		// Validate minimum required arguments instead of exact match
		// This allows commands like 'execute' to accept variable-length arguments
		if (args.length < command.args().length) {
			sender.reply(I18nManager.getDmccTranslation("terminal.unknown_command", (name + " " + String.join(" ", args))));
			return;
		}

		try {
			command.execute(sender, args);
		} catch (Exception e) {
			sender.reply(I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage()));
		}
	}
}
