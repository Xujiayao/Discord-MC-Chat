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
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Central registry and dispatcher for DMCC commands.
 * <p>
 * Commands run on one virtual thread per task, so a command that fans out to N clients and waits for their
 * answers no longer blocks the commands submitted after it. Commands that mutate shared DMCC state keep
 * running one at a time: see {@link #SERIALIZED_COMMANDS}.
 *
 * @author Xujiayao
 */
public final class CommandManager {

	/**
	 * Commands that must not overlap any other command, because they either mutate shared DMCC state or
	 * tear the DMCC runtime down. They used to be serialized by the single command thread; the names below
	 * are the registry keys, so any new command that touches shared state has to be added here.
	 * <ul>
	 *   <li>{@code reload} / {@code shutdown} - rebuild (or tear down) the whole runtime, including this
	 *       registry and {@code commandExecutor}, so nothing else may be running while they do it.</li>
	 *   <li>{@code link} / {@code unlink} - mutate the persisted linked accounts, which are also read by
	 *       the mention directory and the OP sync.</li>
	 *   <li>{@code whitelist} - delegates to the Minecraft server thread and waits for its answer.</li>
	 * </ul>
	 * Everything else ({@code console}, {@code execute}, {@code info}, ...) runs concurrently.
	 */
	private static final Set<String> SERIALIZED_COMMANDS = Set.of("reload", "shutdown", "link", "unlink", "whitelist");

	/**
	 * Guards command execution: ordinary commands share the read lock and run concurrently, while the
	 * commands in {@link #SERIALIZED_COMMANDS} take the write lock and therefore wait for the in-flight
	 * commands to finish and keep new ones out until they are done. Fair, so a queued serialized command
	 * cannot be starved by a continuous stream of concurrent commands.
	 */
	private static final ReentrantReadWriteLock commandLock = new ReentrantReadWriteLock(true);

	/**
	 * The published command registry. {@link #initialize()} always builds a completely fresh map and swaps
	 * it in with a single assignment, so a reader can never observe a half-empty registry while a
	 * {@code /dmcc reload} is re-registering commands.
	 */
	private static volatile Map<String, Command> COMMANDS = new ConcurrentHashMap<>();

	/**
	 * Executes commands on virtual threads. Volatile because {@link #initialize()} and {@link #shutdown()}
	 * may be called from a different thread than {@link #execute(CommandSender, String, String...)}.
	 */
	private static volatile ExecutorService commandExecutor;

	private CommandManager() {
	}

	/**
	 * Initialize and register built-in commands based on the current operating mode.
	 */
	public static void initialize() {
		if (commandExecutor == null || commandExecutor.isShutdown()) {
			commandExecutor = Executors.newThreadPerTaskExecutor(newCommandThreadFactory());
		}

		Map<String, Command> registry = new ConcurrentHashMap<>();

		register(registry, new HelpCommand());
		register(registry, new InfoCommand());
		register(registry, new LogCommand());
		register(registry, new ReloadCommand());
		register(registry, new UpdateCommand());

		switch (ConfigManager.getMode()) {
			case "standalone" -> {
				register(registry, new ConsoleCommand());
				register(registry, new ExecuteCommand());
				register(registry, new LinkCommand());
				register(registry, new LinksCommand());
				register(registry, new ShutdownCommand());
				register(registry, new UnlinkCommand());
			}
			case "single_server" -> {
				register(registry, new ConsoleCommand());
				register(registry, new LinkCommand());
				register(registry, new LinksCommand());
				register(registry, new StatsCommand());
				register(registry, new UnlinkCommand());
				register(registry, new WhitelistCommand());
			}
			case "multi_server_client" -> {
				register(registry, new LinkCommand());
				register(registry, new StatsCommand());
				register(registry, new UnlinkCommand());
				register(registry, new WhitelistCommand());
			}
		}

		// Publish the finished registry in one assignment: readers see either the old or the new set of
		// commands, never a registry that is still being filled in.
		COMMANDS = Collections.unmodifiableMap(registry);
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
	 * Builds the thread factory used to execute commands.
	 * <p>
	 * One virtual thread per command is what removes the head-of-line blocking of the previous
	 * single-threaded executor. {@link ExecutorServiceUtils#newThreadFactory(String)} cannot be used here
	 * because it creates platform threads through {@code new Thread(...)}, which virtual threads do not
	 * allow; the mod class loader is pinned on the virtual threads directly instead, for the same reason
	 * that helper does it (SLF4J/ServiceLoader resource lookup from DMCC worker threads).
	 *
	 * @return A virtual thread factory named after the DMCC command executor.
	 */
	private static ThreadFactory newCommandThreadFactory() {
		ClassLoader modClassLoader = CommandManager.class.getClassLoader();
		ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("DMCC-Command-", 0).factory();

		return runnable -> {
			Thread thread = virtualThreadFactory.newThread(runnable);
			thread.setContextClassLoader(modClassLoader);
			return thread;
		};
	}

	private static void register(Map<String, Command> registry, Command command) {
		registry.put(command.name().toLowerCase(), command);
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
	 * @param sender The command sender
	 * @param name   The command name
	 * @param args   The command arguments (if any)
	 */
	public static void execute(CommandSender sender, String name, String... args) {
		ExecutorService executor = commandExecutor;
		if (executor == null || executor.isShutdown()) {
			return;
		}

		executor.submit(() -> executeInternal(sender, name, args));
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

		ExecutorService executor = commandExecutor;
		if (executor == null || executor.isShutdown()) {
			future.completeExceptionally(new IllegalStateException("Command executor is not available"));
			return future;
		}

		executor.submit(() -> {
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
		// The lock is taken before the registry is read, so a command submitted while a /dmcc reload is
		// swapping the registry waits for the reload and then runs against the new registry.
		Lock lock = SERIALIZED_COMMANDS.contains(name.toLowerCase()) ? commandLock.writeLock() : commandLock.readLock();
		lock.lock();

		try {
			runCommand(sender, name, args);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Resolves and runs a single command from the currently published registry.
	 *
	 * @param sender The command sender
	 * @param name   The command name
	 * @param args   The command arguments (if any)
	 */
	private static void runCommand(CommandSender sender, String name, String... args) {
		// Read the published registry exactly once per operation.
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

		// Too few arguments, or too many for a fixed-arity command: show the command's own usage.
		if (args.length < expectedArgs || (args.length > expectedArgs && !command.acceptsExtraArgs())) {
			sender.reply(I18nManager.getDmccTranslation("commands.invalid_usage", command.usage(command.args())));
			return;
		}

		try {
			command.execute(sender, args);
		} catch (Exception e) {
			sender.reply(I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage()));
		}
	}
}
