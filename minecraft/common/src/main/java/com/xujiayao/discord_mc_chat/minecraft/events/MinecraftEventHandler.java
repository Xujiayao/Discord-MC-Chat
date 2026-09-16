package com.xujiayao.discord_mc_chat.minecraft.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.platform.StatsProvider;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatType;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Handles the Minecraft events reported by the platform mixins and implements the
 * Minecraft side of DMCC core's platform contract.
 * <p>
 * Every handler is a plain static method called directly by its caller: the mixins call
 * the gameplay handlers, and DMCC core calls the rest through its platform interface.
 * <p>
 * Work that needs the server is scheduled on the server thread in one of two ways, and the difference
 * matters:
 * <ul>
 *   <li>{@link #onServerThread(java.util.function.Consumer)} guards the null server and <b>swallows</b>
 *       exceptions, for work whose failure is not worth interrupting the gameplay.</li>
 *   <li>A bare {@code serverInstance.execute(...)} does <b>not</b> catch, so a failure surfaces in the
 *       server tick loop and reaches the crash report. The {@code broadcastDiscord*} methods use this on
 *       purpose: swallowing their exceptions would hide real rendering bugs.</li>
 * </ul>
 * Those two are not duplicates of each other; do not fold one into the other to save lines.
 *
 * @author Xujiayao
 */
public final class MinecraftEventHandler {

	private static final String DEFAULT_MENTION_STYLE = "title";
	private static final String DMCC_SOURCE_NAME = "DMCC";

	/**
	 * Interval between two polls for asynchronous command output.
	 */
	private static final int COMMAND_OUTPUT_POLL_INTERVAL_MILLIS = 100;

	/**
	 * Number of polls before giving up on asynchronous command output (about 5 seconds in total).
	 */
	private static final int COMMAND_OUTPUT_POLL_MAX_ATTEMPTS = 50;

	/**
	 * Safety-net refresh interval for the "players ever joined" metric.
	 */
	private static final long PLAYERS_EVER_JOINED_TTL_MILLIS = Duration.ofMinutes(5).toMillis();

	/**
	 * Set whenever a player joins or quits, so the next info request refreshes the metric.
	 */
	private static final AtomicBoolean PLAYERS_EVER_JOINED_STALE = new AtomicBoolean(true);

	private static volatile int playersEverJoinedCache;
	private static volatile long playersEverJoinedRefreshedAt;
	private static MinecraftServer serverInstance;
	private static StatsProvider statsProviderInstance;

	private MinecraftEventHandler() {
	}

	/**
	 * Called when the server is started.
	 *
	 * @param server The running Minecraft server instance.
	 */
	public static void onServerStarted(MinecraftServer server) {
		serverInstance = server;

		ComponentRenderer.init(server);

		// Seed the "players ever joined" metric while we are already on the server thread, so the first info
		// request reports the real value instead of a placeholder.
		refreshPlayersEverJoined();

		Map<String, String> placeholders = Map.of();
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SERVER_STARTED, placeholders));

		// Initialize translation manager with the started server instance after announcing server started event
		TranslationManager.setServer(server);
		TranslationManager.init();

		// Register info supplier after server is started
		NetworkManager.registerInfoSupplier(() -> buildInfoResponse(server));
	}

	/**
	 * Called when the server is stopping.
	 */
	public static void onServerStopping() {
		Map<String, String> placeholders = Map.of();
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SERVER_STOPPING, placeholders));
	}

	/**
	 * Called when the server is stopped.
	 */
	public static void onServerStopped() {
		// Shutdown DMCC when the server is stopped
		// Blocks until shutdown is complete
		DMCC.shutdown();
	}

	/**
	 * Called when a player joins the server.
	 *
	 * @param player The joining player.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString()
		);
		PLAYERS_EVER_JOINED_STALE.set(true);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_JOIN, placeholders));

		// Account linking: check if this player is linked (via network packet)
		String playerUuid = player.getStringUUID();
		String playerName = player.getName().getString();
		NetworkManager.sendPacketToServer(new Packets.LinkRequest(playerUuid, playerName, true));
	}

	/**
	 * Called when a player leaves the server.
	 *
	 * @param player The leaving player.
	 */
	public static void onPlayerQuit(ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString()
		);
		PLAYERS_EVER_JOINED_STALE.set(true);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_QUIT, placeholders));
	}

	/**
	 * Called when a player dies.
	 *
	 * @param player The player who died.
	 */
	public static void onPlayerDie(ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString(),
				"death_message", TranslationManager.get(player.getCombatTracker().getDeathMessage())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_DIE, placeholders));
	}

	/**
	 * Called when a player makes advancement progress.
	 *
	 * @param holder   The advancement definition.
	 * @param player   The player who made progress.
	 * @param progress The advancement progress state.
	 */
	public static void onPlayerAdvancement(AdvancementHolder holder, ServerPlayer player, AdvancementProgress progress) {
		DisplayInfo displayInfo = holder.value().display().orElse(null);
		if (displayInfo != null
				&& displayInfo.shouldAnnounceChat()
				&& progress.isDone()
				&& player.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
			String type = switch (displayInfo.getType()) {
				case TASK -> "task";
				case CHALLENGE -> "challenge";
				case GOAL -> "goal";
			};

			Map<String, String> placeholders = Map.of(
					"type", type,
					"player_name", player.getName().getString(),
					"display_name", player.getDisplayName().getString(),
					"title", TranslationManager.get(displayInfo.getTitle()),
					"description", TranslationManager.get(displayInfo.getDescription())
			);

			NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_ADVANCEMENT, placeholders));
		}
	}

	/**
	 * Called when a player changes game mode.
	 *
	 * @param type   The new game mode.
	 * @param player The affected player.
	 */
	public static void onPlayerChangeGameMode(GameType type, ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString(),
				"mode", TranslationManager.get(type.getLongDisplayName())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_CHANGE_GAME_MODE, placeholders));
	}

	/**
	 * Called when a player sends a chat message.
	 *
	 * @param message The signed chat message payload.
	 * @param player  The speaking player.
	 */
	public static void onPlayerChat(PlayerChatMessage message, ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", player.getStringUUID(),
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString(),
				"message", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_CHAT, placeholders));
	}

	/**
	 * Called when a player executes a command.
	 *
	 * @param command The raw command string.
	 * @param player  The player executing the command.
	 */
	public static void onPlayerCommand(String command, ServerPlayer player) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", player.getStringUUID(),
				"player_name", player.getName().getString(),
				"display_name", player.getDisplayName().getString(),
				"command", "/" + command
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.PLAYER_COMMAND, placeholders));
	}

	/**
	 * Called when the /say command is used.
	 *
	 * @param source  The command source that produced the message.
	 * @param message The parsed chat message argument.
	 */
	public static void onSourceSay(CommandSourceStack source, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", source.getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", source.getTextName(),
				"display_name", source.getDisplayName().getString(),
				"message", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SOURCE_SAY, placeholders));
	}

	/**
	 * Called when the /tellraw command is used.
	 *
	 * @param source    The command source that produced the message.
	 * @param component The resolved tellraw component.
	 */
	public static void onSourceTellRaw(CommandSourceStack source, Component component) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", source.getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", source.getTextName(),
				"display_name", source.getDisplayName().getString(),
				"message", TranslationManager.get(component),
				"component_json", ComponentRenderer.toJson(component)
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SOURCE_TELL_RAW, placeholders));
	}

	/**
	 * Called when the /msg, /tell, /w command is used.
	 *
	 * @param source  The command source that produced the message.
	 * @param message The parsed private message payload.
	 */
	public static void onSourceMsg(CommandSourceStack source, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", source.getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", source.getTextName(),
				"display_name", source.getDisplayName().getString(),
				"message", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SOURCE_MSG, placeholders));
	}

	/**
	 * Called when the /me command is used.
	 *
	 * @param source  The command source that produced the message.
	 * @param message The parsed emote message payload.
	 */
	public static void onSourceMe(CommandSourceStack source, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_name", source.getTextName(),
				"display_name", source.getDisplayName().getString(),
				"action", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new Packets.MinecraftEvent(Packets.MinecraftEventType.SOURCE_ME, placeholders));
	}

	/**
	 * Called when commands are being registered.
	 *
	 * @param dispatcher The command dispatcher to register into.
	 */
	public static void onCommandRegister(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Register Minecraft /dmcc commands
		MinecraftCommands.register(dispatcher);
	}

	/**
	 * Called when Minecraft is reloading resources.
	 */
	public static void onReloadResources() {
		// Refresh Minecraft translations
		TranslationManager.init();
	}

	// ===== Minecraft Command Execution Bridge =====

	/**
	 * Executes a Minecraft command on behalf of a DMCC sender.
	 *
	 * @param sender      The command sender bridging the execution, used for replying results.
	 * @param commandLine The raw Minecraft command line to be executed (without leading slash).
	 * @param completion  Completed once the command output has been collected and replied.
	 */
	public static void executeCommand(CommandSender sender, String commandLine, CompletableFuture<Void> completion) {
		if (serverInstance == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.console.server_not_ready"));
			completion.complete(null);
			return;
		}

		// Discord visitors (-1) default to OP 0 for Minecraft's permission system
		int mcOp = Math.max(0, sender.getOpLevel());

		// Construct a virtual CommandSourceStack with the sender's OP level
		// and a custom CommandSource that bridges output back to the DMCC sender
		DmccRconConsoleSource rconConsoleSource = new DmccRconConsoleSource(serverInstance);
		CommandSourceStack source = dmccSource(rconConsoleSource, mcOp);

		// Must be dispatched to the main server thread to avoid concurrent modification.
		// The completion future is completed after the command has been executed on the server thread,
		// ensuring all output has been sent to the sender before the response is collected.
		serverInstance.execute(() -> {
			try {
				serverInstance.getCommands().performPrefixedCommand(source, commandLine);
			} finally {
				// First check if there's an immediate response
				// (for simple commands that execute synchronously and produce output right away)
				if (!rconConsoleSource.getCommandResponse().isEmpty()) {
					sender.reply(rconConsoleSource.getCommandResponse());
					completion.complete(null);
				} else {
					// For commands that execute asynchronously or produce output after a delay
					// (e.g. due to network calls, database access, or scheduled tasks) the wait runs on a
					// virtual thread: polling used to occupy a ForkJoinPool worker for up to 5 seconds.
					Thread.ofVirtual().name("DMCC-CommandOutput").start(() -> {
						awaitCommandOutput(rconConsoleSource);

						// Send any collected command output back to the sender
						sender.reply(rconConsoleSource.getCommandResponse());
						completion.complete(null);
					});
				}
			}
		});
	}

	/**
	 * Gathers Minecraft command auto-complete suggestions for the given input.
	 *
	 * @param input       The current input string to auto-complete.
	 * @param opLevel     The OP level of the user requesting auto-complete.
	 * @param suggestions The mutable list of suggestions to append to.
	 */
	public static void autoCompleteCommand(String input, int opLevel, List<String> suggestions) {
		if (serverInstance == null) return;

		int mcOp = Math.max(0, opLevel);

		CommandSourceStack source = dmccSource(mcOp);

		String rawInput = input == null ? "" : input;

		try {
			List<String> currentResult = getSuggestionsForInput(rawInput, source);

			if (!rawInput.isBlank() && !rawInput.endsWith(" ") && isExactPath(rawInput, source)) {
				List<String> nextResult = getSuggestionsForInput(rawInput + " ", source);

				// Priority:
				// 1) self (only if self is a valid candidate)
				// 2) suggestions for "<input> "
				// 3) current suggestions
				Set<String> added = new HashSet<>();

				if (isSelfCandidate(rawInput, source, nextResult, currentResult)) {
					added.add(rawInput);
					suggestions.add(rawInput);
				}

				for (String s : nextResult) {
					if (added.add(s)) {
						suggestions.add(s);
					}
				}

				for (String s : currentResult) {
					if (added.add(s)) {
						suggestions.add(s);
					}
				}
				return;
			}

			suggestions.addAll(currentResult);
		} catch (Exception ignored) {
		}
	}

	/**
	 * Builds the virtual command source used to execute a DMCC command on the server.
	 *
	 * @param consoleSource The console source collecting the command output.
	 * @param opLevel       The OP level to grant the source.
	 */
	private static CommandSourceStack dmccSource(DmccRconConsoleSource consoleSource, int opLevel) {
		MinecraftServer server = serverInstance;
		return new CommandSourceStack(
				consoleSource,
				Vec3.atLowerCornerOf(server.getRespawnData().pos()),
				Vec2.ZERO,
				server.findRespawnDimension(),
				LevelBasedPermissionSet.forLevel(PermissionLevel.byId(opLevel)),
				DMCC_SOURCE_NAME,
				Component.literal(DMCC_SOURCE_NAME),
				server,
				null
		);
	}

	/**
	 * Builds the virtual command source used to auto-complete a DMCC command on the server.
	 *
	 * @param opLevel The OP level to grant the source.
	 */
	private static CommandSourceStack dmccSource(int opLevel) {
		return dmccSource(new DmccRconConsoleSource(serverInstance), opLevel);
	}

	// ===== Account Linking Feedback =====

	/**
	 * Notifies a Minecraft player about their account linking verification code.
	 *
	 * @param playerUuid    The UUID of the Minecraft player.
	 * @param code          The verification code, or null if already linked.
	 * @param alreadyLinked Whether the player is already linked.
	 * @param discordName   The Discord username if already linked (for display), or empty string.
	 */
	public static void sendLinkCode(String playerUuid, String code, boolean alreadyLinked, String discordName) {
		if (serverInstance == null) return;

		UUID uuid;
		try {
			uuid = UUID.fromString(playerUuid);
		} catch (IllegalArgumentException e) {
			// Malformed UUID from the server side; nothing we could notify
			return;
		}

		// Find the player and notify them
		onServerThread(server -> {
			try {
				ServerPlayer player = server.getPlayerList().getPlayer(uuid);
				if (player != null) {
					if (alreadyLinked) {
						player.sendSystemMessage(buildAlreadyLinkedMessage(discordName));
					} else if (code != null) {
						player.sendSystemMessage(buildNotLinkedMessage(code));
					}
				}
			} catch (Exception ignored) {
			}
		});
	}

	/**
	 * Notifies a Minecraft player about an unlink result.
	 *
	 * @param playerUuid  The UUID of the Minecraft player.
	 * @param success     Whether the unlink was successful.
	 * @param discordName The Discord username that was unlinked from (for display), or empty string.
	 */
	public static void sendUnlinkResult(String playerUuid, boolean success, String discordName) {
		if (serverInstance == null) return;

		UUID uuid;
		try {
			uuid = UUID.fromString(playerUuid);
		} catch (IllegalArgumentException e) {
			// Malformed UUID from the server side; nothing we could notify
			return;
		}

		onServerThread(server -> {
			try {
				ServerPlayer player = server.getPlayerList().getPlayer(uuid);
				if (player != null) {
					if (success) {
						player.sendSystemMessage(Component.literal(
								I18nManager.getDmccTranslation("commands.unlink.success_minecraft", discordName)));
					} else {
						player.sendSystemMessage(Component.literal(
								I18nManager.getDmccTranslation("commands.unlink.not_linked")));
					}
				}
			} catch (Exception ignored) {
			}
		});
	}

	// ===== OP Level Sync =====

	/**
	 * Applies DMCC's authoritative OP levels to the Minecraft server OP list.
	 *
	 * @param opLevels Map of Minecraft UUID to the desired OP level (0-4).
	 */
	public static void applyOpLevels(Map<String, Integer> opLevels) {
		onServerThread(server -> {
			try {
				PlayerList playerList = server.getPlayerList();
				ServerOpList opList = playerList.getOps();

				// Build a map of current OP levels: UUID -> level
				Map<UUID, Integer> currentOpLevels = new HashMap<>();
				for (ServerOpListEntry entry : opList.getEntries()) {
					try {
						NameAndId user = entry.getUser();
						if (user == null) continue;
						UUID uuid = UUID.fromString(user.id().toString());
						// Read level from entry
						int level = entry.permissions().level().id();
						if (level >= 0) currentOpLevels.put(uuid, level);
					} catch (Exception ignored) {
					}
				}

				// Build desired OP levels map from the requested levels (UUID -> level), skipping non-positive levels
				Map<UUID, Integer> desiredOpLevels = new HashMap<>();
				for (Map.Entry<String, Integer> e : opLevels.entrySet()) {
					int level = e.getValue();
					if (level <= 0) continue;
					try {
						desiredOpLevels.put(UUID.fromString(e.getKey()), level);
					} catch (IllegalArgumentException ignored) {
						// Malformed UUID from the server side; skip this entry
					}
				}

				// Quick equality check: if both maps equal, skip save and permission update
				if (currentOpLevels.equals(desiredOpLevels)) {
					return;
				}

				boolean changed = false;

				// De-op users that are currently opped but not desired
				for (ServerOpListEntry entry : new ArrayList<>(opList.getEntries())) {
					NameAndId user = entry.getUser();
					if (user == null) continue;
					try {
						UUID uuid = UUID.fromString(user.id().toString());
						if (!desiredOpLevels.containsKey(uuid)) {
							playerList.deop(user);
							changed = true;
						}
					} catch (Exception ignored) {
					}
				}

				// Add/update desired OP entries
				for (Map.Entry<UUID, Integer> e : desiredOpLevels.entrySet()) {
					UUID uuid = e.getKey();
					int level = e.getValue();
					Optional<NameAndId> nameAndIdOpt = server.services().nameToIdCache().get(uuid);
					if (nameAndIdOpt.isEmpty()) {
						// Profile not in cache; skip this entry
						continue;
					}
					NameAndId nameAndId = nameAndIdOpt.get();
					// If current level equals desired, skip; otherwise add (or re-add) entry
					Integer currentLevel = currentOpLevels.get(uuid);
					if (currentLevel != null && currentLevel == level) {
						continue;
					}
					opList.add(new ServerOpListEntry(nameAndId, LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)), opList.canBypassPlayerLimit(nameAndId)));
					changed = true;
				}

				if (changed) {
					try {
						opList.save();
					} catch (Exception ignored) {
					}

					// Update permission levels for online players only if OP list was modified
					for (ServerPlayer player : playerList.getPlayers()) {
						playerList.sendPlayerPermissionLevel(player);
					}
				}
			} catch (Exception ignored) {
			}
		});
	}

	// ===== Discord Message Rendering =====

	/**
	 * Broadcasts a Discord chat message (with optional reply line and mention notification).
	 *
	 * @param segments        The main message line segments.
	 * @param replySegments   The reply context line segments (may be null or empty).
	 * @param mentionText     The mention notification text (may be null).
	 * @param mentionStyle    The notification style: "action_bar", "title", or "chat".
	 * @param mentionedUuids  UUIDs of players who should receive mention notifications.
	 * @param mentionEveryone Whether @everyone should be treated as a global mention in Minecraft.
	 */
	public static void broadcastDiscordChat(List<TextSegment> segments, List<TextSegment> replySegments,
											String mentionText, String mentionStyle,
											List<String> mentionedUuids, boolean mentionEveryone) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> {
			PlayerList playerList = serverInstance.getPlayerList();

			broadcastLines(replySegments, segments);

			if (mentionText != null) {
				Component notificationComponent = Component.literal(mentionText)
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				notifyMentionedPlayers(playerList, notificationComponent, mentionStyle, mentionedUuids, mentionEveryone);
			}
		});
	}

	/**
	 * Broadcasts a Discord command execution notification.
	 *
	 * @param segments The command notification segments.
	 */
	public static void broadcastDiscordCommand(List<TextSegment> segments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> broadcastLines(segments));
	}

	/**
	 * Broadcasts a Discord reaction notification.
	 *
	 * @param segments      The reaction notification segments.
	 * @param replySegments The reference to the original message.
	 */
	public static void broadcastDiscordReaction(List<TextSegment> segments, List<TextSegment> replySegments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> broadcastLines(replySegments, segments));
	}

	/**
	 * Broadcasts a Discord message edit notification.
	 *
	 * @param segments              The edit notification segments.
	 * @param replySegments         The reference to the original (pre-edit) message.
	 * @param editedMessageSegments The new (edited) message formatted as chat.
	 */
	public static void broadcastDiscordEdit(List<TextSegment> segments, List<TextSegment> replySegments,
											List<TextSegment> editedMessageSegments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> broadcastLines(replySegments, segments, editedMessageSegments));
	}

	/**
	 * Broadcasts a Discord message deletion notification.
	 *
	 * @param segments      The delete notification segments.
	 * @param replySegments The reference to the deleted message.
	 */
	public static void broadcastDiscordDelete(List<TextSegment> segments, List<TextSegment> replySegments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> broadcastLines(replySegments, segments));
	}

	/**
	 * Broadcasts a message relayed from another DMCC client.
	 *
	 * @param segments             The parsed message segments.
	 * @param componentJson        Serialized Minecraft component JSON for native tellraw relays, or null.
	 * @param componentPlaceholder Placeholder token in {@code segments} to be replaced by {@code componentJson}, or null.
	 * @param mentionText          Mention notification text, or null.
	 * @param mentionStyle         Mention notification style.
	 * @param mentionedUuids       UUIDs to receive mention notifications.
	 * @param mentionEveryone      Whether @everyone should be treated as a global mention in Minecraft.
	 */
	public static void broadcastMinecraftRelay(List<TextSegment> segments, String componentJson,
											   String componentPlaceholder, String mentionText,
											   String mentionStyle, List<String> mentionedUuids,
											   boolean mentionEveryone) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> {
			PlayerList playerList = serverInstance.getPlayerList();
			Component component;
			if (componentJson != null && !componentJson.isBlank()) {
				Component nativeComponent = ComponentRenderer.fromJson(componentJson);
				if (nativeComponent != null && componentPlaceholder != null && !componentPlaceholder.isBlank()) {
					component = ComponentRenderer.toComponentReplacingPlaceholder(segments, componentPlaceholder, nativeComponent);
				} else
					component = Objects.requireNonNullElseGet(nativeComponent, () -> ComponentRenderer.toComponent(segments));
			} else {
				component = ComponentRenderer.toComponent(segments);
			}

			broadcast(component);

			if (mentionText != null) {
				Component notificationComponent = Component.literal(mentionText)
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				notifyMentionedPlayers(playerList, notificationComponent, mentionStyle, mentionedUuids, mentionEveryone);
			}
		});
	}

	// ===== Stats Provider =====

	/**
	 * Gets the Minecraft statistics provider.
	 * <p>
	 * The provider is created lazily on first use and reads from the running server instance.
	 *
	 * @return The Minecraft stats provider.
	 */
	public static StatsProvider statsProvider() {
		StatsProvider provider = statsProviderInstance;
		if (provider == null) {
			provider = new MinecraftStatsProvider(serverInstance);
			statsProviderInstance = provider;
		}
		return provider;
	}

	private static List<String> getSuggestionsForInput(String input, CommandSourceStack source) throws Exception {
		ParseResults<CommandSourceStack> parse = serverInstance.getCommands().getDispatcher().parse(input, source);

		Suggestions suggestions = serverInstance.getCommands().getDispatcher()
				.getCompletionSuggestions(parse)
				.get(3, TimeUnit.SECONDS);

		boolean isRootToken = !input.contains(" ");
		Set<String> allowedRoot = new HashSet<>();
		for (CommandNode<CommandSourceStack> child : serverInstance.getCommands().getDispatcher().getRoot().getChildren()) {
			if (!child.getName().isEmpty() && child.canUse(source)) {
				allowedRoot.add(child.getName());
			}
		}

		List<String> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Suggestion suggestion : suggestions.getList()) {
			if (isRootToken && !allowedRoot.contains(suggestion.getText())) {
				continue;
			}
			String applied = suggestion.apply(input);
			if (seen.add(applied)) {
				result.add(applied);
			}
		}
		return result;
	}

	private static boolean isExactPath(String input, CommandSourceStack source) {
		ParseResults<CommandSourceStack> parse = serverInstance.getCommands().getDispatcher().parse(input, source);
		CommandContextBuilder<CommandSourceStack> ctx = parse.getContext();

		if (ctx.getRange().getEnd() != input.length()) {
			return false;
		}

		if (!parse.getExceptions().isEmpty()) {
			return false;
		}

		return !ctx.getNodes().isEmpty();
	}

	private static boolean isSelfCandidate(String rawInput,
										   CommandSourceStack source,
										   List<String> nextResult,
										   List<String> currentResult) {
		// Fast path: already present in computed suggestions.
		if (nextResult.contains(rawInput) || currentResult.contains(rawInput)) {
			return true;
		}

		// Backspace test:
		// if input-1 can suggest rawInput, treat rawInput as a valid candidate.
		if (rawInput.length() <= 1) {
			return false;
		}

		String backspaced = rawInput.substring(0, rawInput.length() - 1);
		try {
			List<String> fromBackspaced = getSuggestionsForInput(backspaced, source);
			return fromBackspaced.contains(rawInput);
		} catch (Exception ignored) {
			return false;
		}
	}

	private static Component buildNotLinkedMessage(String code) {
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_1")))
				.append(buildCopyToClipboard("/link code: " + code))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_2")))
				.append(buildSuggestCommand("/dmcc link"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_3")));
	}

	private static Component buildAlreadyLinkedMessage(String discordName) {
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_1", discordName)))
				.append(buildSuggestCommand("/dmcc unlink"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_2")));
	}

	private static Component buildCopyToClipboard(String text) {
		return Component.literal("[" + text + "]").withStyle(style -> style
				.withClickEvent(new ClickEvent.CopyToClipboard(text))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal(I18nManager.getDmccTranslation("linking.tooltip.click_to_copy"))))
				.withColor(ChatFormatting.GREEN));
	}

	private static Component buildSuggestCommand(String command) {
		return Component.literal("[" + command + "]").withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal(I18nManager.getDmccTranslation("linking.tooltip.click_to_run"))))
				.withColor(ChatFormatting.GREEN));
	}

	private static Packets.InfoSnapshot buildInfoResponse(MinecraftServer server) {
		String serverName = "single_server".equals(ConfigManager.getMode()) ? "Internal" : ConfigManager.getString("multi_server.server_name");
		String minecraftVersion = EnvironmentUtils.getMinecraftVersion();

		Map<String, Integer> playersAndLatencies = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (PlayerVisibility.isPlayerHidden(player)) {
				continue;
			}
			playersAndLatencies.put(player.getDisplayName().getString(), player.connection.latency());
		}

		int onlinePlayers = playersAndLatencies.size();
		int maxPlayers = server.getPlayerList().getMaxPlayers();

		int playersEverJoined = playersEverJoined();

		double mspt = ((double) server.getAverageTickTimeNanos()) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
		ServerTickRateManager manager = server.tickRateManager();
		double tps = 1000.0D / Math.max(manager.isSprinting() ? 0.0 : manager.millisecondsPerTick(), mspt);
		if (manager.isFrozen()) {
			tps = 0;
		}

		long uptimeSeconds = TimeUnit.MILLISECONDS.toSeconds(ManagementFactory.getRuntimeMXBean().getUptime());

		Runtime runtime = Runtime.getRuntime();
		return new Packets.InfoSnapshot(
				0L,
				serverName,
				-1,
				minecraftVersion,
				onlinePlayers,
				maxPlayers,
				playersAndLatencies,
				playersEverJoined,
				tps,
				mspt,
				uptimeSeconds,
				runtime.totalMemory(),
				runtime.freeMemory()
		);
	}

	/**
	 * Runs work on the server thread when a server is available.
	 *
	 * @param action Work to run on the server thread.
	 */
	/**
	 * Recomputes the "players ever joined" metric on the server thread.
	 * <p>
	 * The underlying stats scan calls {@code PlayerList.saveAll()}, which must run on the server thread, and
	 * it parses every player's stats file. Info requests arrive every 10 seconds from the MSPT monitor, so
	 * the value is cached and only recomputed when a player joins or quits (or after the safety-net TTL).
	 */
	private static void refreshPlayersEverJoined() {
		playersEverJoinedCache = statsProvider().countPlayersEverJoined();
		playersEverJoinedRefreshedAt = System.currentTimeMillis();
		PLAYERS_EVER_JOINED_STALE.set(false);
	}

	/**
	 * @return The cached "players ever joined" count, queueing a server-thread refresh when it is stale.
	 */
	private static int playersEverJoined() {
		boolean expired = System.currentTimeMillis() - playersEverJoinedRefreshedAt > PLAYERS_EVER_JOINED_TTL_MILLIS;
		if (PLAYERS_EVER_JOINED_STALE.compareAndSet(true, false) || expired) {
			onServerThread(_ -> refreshPlayersEverJoined());
		}
		return playersEverJoinedCache;
	}

	/**
	 * Waits until asynchronous command output shows up, or until the poll budget runs out.
	 * <p>
	 * The first non-empty response triggers one extra interval, so output that is produced in several
	 * chunks is collected as well.
	 *
	 * @param source The console source collecting the command output.
	 */
	private static void awaitCommandOutput(DmccRconConsoleSource source) {
		for (int attempt = 0; attempt < COMMAND_OUTPUT_POLL_MAX_ATTEMPTS; attempt++) {
			if (!source.getCommandResponse().isEmpty()) {
				sleepCommandOutputInterval();
				return;
			}
			if (!sleepCommandOutputInterval()) {
				return;
			}
		}
	}

	private static boolean sleepCommandOutputInterval() {
		try {
			Thread.sleep(COMMAND_OUTPUT_POLL_INTERVAL_MILLIS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static void onServerThread(Consumer<MinecraftServer> action) {
		MinecraftServer server = serverInstance;
		if (server == null) {
			return;
		}

		server.execute(() -> {
			try {
				action.accept(server);
			} catch (Exception ignored) {
			}
		});
	}

	/**
	 * Sends a component to every online player.
	 *
	 * @param component The component to send.
	 */
	@SafeVarargs
	private static void broadcastLines(List<TextSegment>... lineGroups) {
		for (List<TextSegment> group : lineGroups) {
			if (group != null && !group.isEmpty()) {
				broadcast(ComponentRenderer.toComponent(group));
			}
		}
	}

	private static void broadcast(Component component) {
		for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
			player.sendSystemMessage(component);
		}
	}

	/**
	 * Sends mention notifications to the players that should receive them.
	 *
	 * @param playerList      The server player list to notify.
	 * @param notification    The notification component to display.
	 * @param mentionStyle    The notification style: "action_bar", "title", or "chat".
	 * @param mentionedUuids  UUIDs of the players to notify for direct/role mentions.
	 * @param mentionEveryone Whether @everyone should notify every online player.
	 */
	private static void notifyMentionedPlayers(PlayerList playerList, Component notification,
											   String mentionStyle, List<String> mentionedUuids,
											   boolean mentionEveryone) {
		if (mentionEveryone) {
			// @everyone/@here: notify ALL online players
			for (ServerPlayer player : playerList.getPlayers()) {
				sendMentionNotification(player, notification, mentionStyle);
			}
		} else if (mentionedUuids != null && !mentionedUuids.isEmpty()) {
			// Direct/role mentions: notify specific players
			for (String uuidStr : mentionedUuids) {
				try {
					ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
					if (player != null) {
						sendMentionNotification(player, notification, mentionStyle);
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static void sendMentionNotification(ServerPlayer player, Component component, String style) {
		if (style == null) {
			style = DEFAULT_MENTION_STYLE;
		}

		switch (style) {
			case "action_bar" -> player.connection.send(new ClientboundSetActionBarTextPacket(component));
			case "chat" -> player.sendSystemMessage(component);
			default -> {
				// "title" and any unrecognized style fallback to title display
				player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
				player.connection.send(new ClientboundSetTitleTextPacket(component));
				player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
			}
		}

		player.connection.send(new ClientboundSoundPacket(
				SoundEvents.NOTE_BLOCK_PLING,
				SoundSource.MASTER,
				player.getX(),
				player.getY(),
				player.getZ(),
				1.0F,
				2.0F,
				player.getRandom().nextLong()
		));
	}
}
