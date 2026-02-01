package com.xujiayao.discord_mc_chat.commands;

import com.xujiayao.discord_mc_chat.commands.impl.HelpCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ReloadCommand;
import com.xujiayao.discord_mc_chat.commands.impl.ShutdownCommand;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
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
		register(new ReloadCommand());

		if ("standalone".equals(ModeManager.getMode())) {
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
	 * Execute a command line.
	 *
	 * @param sender   The command sender
	 * @param rawInput The raw command line
	 */
	public static void execute(CommandSender sender, String rawInput) {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			return;
		}

		commandExecutor.submit(() -> {
			String line = rawInput == null ? "" : rawInput.trim();
			if (line.isEmpty()) {
				sender.reply(I18nManager.getDmccTranslation("terminal.unknown_command", rawInput));
				return;
			}

			String[] parts = line.split("\\s+");
			String name = parts[0].toLowerCase();
			String[] args = parts.length > 1 ? line.substring(line.indexOf(' ') + 1).split("\\s+") : new String[0];

			Command command = COMMANDS.get(name);
			if (command == null) {
				sender.reply(I18nManager.getDmccTranslation("terminal.unknown_command", rawInput));
				return;
			}

			try {
				command.execute(sender, args);
			} catch (Exception e) {
				sender.reply(I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage()));
			}
		});
	}
}
