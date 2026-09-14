package com.xujiayao.discord_mc_chat.minecraft.events;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.JsonOps;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.mod.ModIntegrations;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Info.ResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.RequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftEventPacket;
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
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
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
import java.net.URI;
import java.nio.file.Path;
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

/**
 * Handles the Minecraft events reported by the platform mixins and implements the
 * Minecraft side of DMCC core's platform contract.
 * <p>
 * Every handler is a plain static method called directly by its caller: the mixins call
 * the gameplay handlers, and DMCC core calls the rest through its platform interface.
 *
 * @author Xujiayao
 */
public final class MinecraftEventHandler {

	private static final String DEFAULT_MENTION_STYLE = "title";
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

		Map<String, String> placeholders = Map.of();
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STARTED, placeholders));

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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STOPPING, placeholders));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_JOIN, placeholders));

		// Account linking: check if this player is linked (via network packet)
		String playerUuid = player.getStringUUID();
		String playerName = player.getName().getString();
		NetworkManager.sendPacketToServer(new RequestPacket(playerUuid, playerName, true));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_QUIT, placeholders));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_DIE, placeholders));
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

			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_ADVANCEMENT, placeholders));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_CHANGE_GAME_MODE, placeholders));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_CHAT, placeholders));
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
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_COMMAND, placeholders));
	}

	/**
	 * Called when the /say command is used.
	 *
	 * @param context The Brigadier command context.
	 * @param message The parsed chat message argument.
	 */
	public static void onSourceSay(CommandContext<CommandSourceStack> context, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", context.getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", context.getSource().getTextName(),
				"display_name", context.getSource().getDisplayName().getString(),
				"message", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_SAY, placeholders));
	}

	/**
	 * Called when the /tellraw command is used.
	 *
	 * @param context   The Brigadier command context.
	 * @param component The resolved tellraw component.
	 */
	public static void onSourceTellRaw(CommandContext<CommandSourceStack> context, Component component) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", context.getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", context.getSource().getTextName(),
				"display_name", context.getSource().getDisplayName().getString(),
				"message", TranslationManager.get(component),
				"component_json", serializeComponent(component)
		);
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_TELL_RAW, placeholders));
	}

	/**
	 * Called when the /msg, /tell, /w command is used.
	 *
	 * @param context The Brigadier command context.
	 * @param message The parsed private message payload.
	 */
	public static void onSourceMsg(CommandContext<CommandSourceStack> context, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_uuid", context.getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
				"player_name", context.getSource().getTextName(),
				"display_name", context.getSource().getDisplayName().getString(),
				"message", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_MSG, placeholders));
	}

	/**
	 * Called when the /me command is used.
	 *
	 * @param context The Brigadier command context.
	 * @param message The parsed emote message payload.
	 */
	public static void onSourceMe(CommandContext<CommandSourceStack> context, PlayerChatMessage message) {
		Map<String, String> placeholders = Map.of(
				"player_name", context.getSource().getTextName(),
				"display_name", context.getSource().getDisplayName().getString(),
				"action", TranslationManager.get(message.decoratedContent())
		);
		NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_ME, placeholders));
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
		CommandSourceStack source = new CommandSourceStack(
				rconConsoleSource,
				Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
				Vec2.ZERO,
				serverInstance.findRespawnDimension(),
				LevelBasedPermissionSet.forLevel(PermissionLevel.byId(mcOp)),
				"DMCC",
				Component.literal("DMCC"),
				serverInstance,
				null
		);

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
					// (e.g. due to network calls, database access, or scheduled tasks)
					CompletableFuture.runAsync(() -> {
						// Wait for up to 5 seconds for command output to be produced, checking every 100ms,
						// and wait an extra 100ms after the first non-empty response.
						for (int i = 0; i < 50; i++) {
							if (!rconConsoleSource.getCommandResponse().isEmpty()) {
								// Extra 100ms wait to allow for any additional output to be produced
								try {
									Thread.sleep(100);
								} catch (InterruptedException ie) {
									Thread.currentThread().interrupt();
									break;
								}
								break;
							}
							try {
								Thread.sleep(100);
							} catch (InterruptedException ie) {
								Thread.currentThread().interrupt();
								break;
							}
						}

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

		CommandSourceStack source = new CommandSourceStack(
				new DmccRconConsoleSource(serverInstance),
				Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
				Vec2.ZERO,
				serverInstance.findRespawnDimension(),
				LevelBasedPermissionSet.forLevel(PermissionLevel.byId(mcOp)),
				"DMCC",
				Component.literal("DMCC"),
				serverInstance,
				null
		);

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
		serverInstance.execute(() -> {
			try {
				ServerPlayer player = serverInstance.getPlayerList().getPlayer(uuid);
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

		serverInstance.execute(() -> {
			try {
				ServerPlayer player = serverInstance.getPlayerList().getPlayer(uuid);
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
		if (serverInstance == null) return;

		serverInstance.execute(() -> {
			try {
				PlayerList playerList = serverInstance.getPlayerList();
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
					Optional<NameAndId> nameAndIdOpt = serverInstance.services().nameToIdCache().get(uuid);
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

			// Build and broadcast the reply line first (if present)
			if (replySegments != null && !replySegments.isEmpty()) {
				Component replyComponent = buildComponentFromSegments(replySegments);
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(replyComponent);
				}
			}

			// Build and broadcast the main message line
			Component mainComponent = buildComponentFromSegments(segments);
			for (ServerPlayer player : playerList.getPlayers()) {
				player.sendSystemMessage(mainComponent);
			}

			// Send mention notifications
			if (mentionText != null) {
				Component notificationComponent = Component.literal(mentionText)
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

				if (mentionEveryone) {
					// @everyone/@here: notify ALL online players
					for (ServerPlayer player : playerList.getPlayers()) {
						sendMentionNotification(player, notificationComponent, mentionStyle);
					}
				} else if (mentionedUuids != null && !mentionedUuids.isEmpty()) {
					// Direct/role mentions: notify specific players
					for (String uuidStr : mentionedUuids) {
						try {
							ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
							if (player != null) {
								sendMentionNotification(player, notificationComponent, mentionStyle);
							}
						} catch (Exception ignored) {
						}
					}
				}
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

		serverInstance.execute(() -> {
			Component component = buildComponentFromSegments(segments);
			for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
				player.sendSystemMessage(component);
			}
		});
	}

	/**
	 * Broadcasts a Discord reaction notification.
	 *
	 * @param segments      The reaction notification segments.
	 * @param replySegments The reference to the original message.
	 */
	public static void broadcastDiscordReaction(List<TextSegment> segments, List<TextSegment> replySegments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> {
			PlayerList playerList = serverInstance.getPlayerList();

			if (replySegments != null && !replySegments.isEmpty()) {
				Component replyComponent = buildComponentFromSegments(replySegments);
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(replyComponent);
				}
			}

			Component component = buildComponentFromSegments(segments);
			for (ServerPlayer player : playerList.getPlayers()) {
				player.sendSystemMessage(component);
			}
		});
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

		serverInstance.execute(() -> {
			PlayerList playerList = serverInstance.getPlayerList();

			if (replySegments != null && !replySegments.isEmpty()) {
				Component replyComponent = buildComponentFromSegments(replySegments);
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(replyComponent);
				}
			}

			// Send edit notification
			Component notificationComponent = buildComponentFromSegments(segments);
			for (ServerPlayer player : playerList.getPlayers()) {
				player.sendSystemMessage(notificationComponent);
			}

			// Send edited message content
			if (editedMessageSegments != null && !editedMessageSegments.isEmpty()) {
				Component editedComponent = buildComponentFromSegments(editedMessageSegments);
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(editedComponent);
				}
			}
		});
	}

	/**
	 * Broadcasts a Discord message deletion notification.
	 *
	 * @param segments      The delete notification segments.
	 * @param replySegments The reference to the deleted message.
	 */
	public static void broadcastDiscordDelete(List<TextSegment> segments, List<TextSegment> replySegments) {
		if (serverInstance == null) return;

		serverInstance.execute(() -> {
			PlayerList playerList = serverInstance.getPlayerList();

			if (replySegments != null && !replySegments.isEmpty()) {
				Component replyComponent = buildComponentFromSegments(replySegments);
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(replyComponent);
				}
			}

			Component component = buildComponentFromSegments(segments);
			for (ServerPlayer player : playerList.getPlayers()) {
				player.sendSystemMessage(component);
			}
		});
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
				Component nativeComponent = deserializeComponent(componentJson);
				if (nativeComponent != null && componentPlaceholder != null && !componentPlaceholder.isBlank()) {
					component = buildComponentFromSegmentsReplacingPlaceholder(segments, componentPlaceholder, nativeComponent);
				} else
					component = Objects.requireNonNullElseGet(nativeComponent, () -> buildComponentFromSegments(segments));
			} else {
				component = buildComponentFromSegments(segments);
			}

			for (ServerPlayer player : playerList.getPlayers()) {
				player.sendSystemMessage(component);
			}

			if (mentionText != null) {
				Component notificationComponent = Component.literal(mentionText)
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				if (mentionEveryone) {
					for (ServerPlayer player : playerList.getPlayers()) {
						sendMentionNotification(player, notificationComponent, mentionStyle);
					}
				} else if (mentionedUuids != null && !mentionedUuids.isEmpty()) {
					for (String uuidStr : mentionedUuids) {
						try {
							ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
							if (player != null) {
								sendMentionNotification(player, notificationComponent, mentionStyle);
							}
						} catch (Exception ignored) {
						}
					}
				}
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
		if (statsProviderInstance == null) {
			statsProviderInstance = new StatsProvider() {
				@Override
				public void saveAll() {
					serverInstance.getPlayerList().saveAll();
				}

				@Override
				public Path getStatsDirectory() {
					return serverInstance.getWorldPath(LevelResource.PLAYER_STATS_DIR);
				}

				@Override
				public String getPlayerName(UUID uuid) {
					return serverInstance.services().nameToIdCache()
							.get(uuid)
							.map(NameAndId::name)
							.orElse(null);
				}

				@Override
				public List<String> getStatTypes() {
					List<String> types = new ArrayList<>();
					for (Identifier loc : BuiltInRegistries.STAT_TYPE.keySet()) {
						types.add(loc.toString());
					}
					return types;
				}

				@Override
				public List<String> getStatNames(String typeStr) {
					List<String> stats = new ArrayList<>();
					try {
						Identifier typeLoc = Identifier.parse(StatsCommand.normalizeMinecraftNamespace(typeStr));
						Optional<Holder.Reference<StatType<?>>> optional = BuiltInRegistries.STAT_TYPE.get(typeLoc);
						if (optional.isPresent()) {
							for (Identifier loc : optional.get().value().getRegistry().keySet()) {
								stats.add(loc.toString());
							}
						}
					} catch (Exception ignored) {
					}
					return stats;
				}
			};
		}
		return statsProviderInstance;
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

	private static ResponsePacket buildInfoResponse(MinecraftServer server) {
		String serverName = "single_server".equals(ConfigManager.getMode()) ? "Internal" : ConfigManager.getString("multi_server.server_name");
		String minecraftVersion = EnvironmentUtils.getMinecraftVersion();

		Map<String, Integer> playersAndLatencies = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ModIntegrations.isPlayerHidden(player)) {
				continue;
			}
			playersAndLatencies.put(player.getDisplayName().getString(), player.connection.latency());
		}

		int onlinePlayers = playersAndLatencies.size();
		int maxPlayers = server.getPlayerList().getMaxPlayers();

		int playersEverJoined = StatsCommand.countStatResultEntries("minecraft:custom", "minecraft:play_time");

		double mspt = ((double) server.getAverageTickTimeNanos()) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
		ServerTickRateManager manager = server.tickRateManager();
		double tps = 1000.0D / Math.max(manager.isSprinting() ? 0.0 : manager.millisecondsPerTick(), mspt);
		if (manager.isFrozen()) {
			tps = 0;
		}

		long uptimeSeconds = TimeUnit.MILLISECONDS.toSeconds(ManagementFactory.getRuntimeMXBean().getUptime());

		Runtime runtime = Runtime.getRuntime();
		return new ResponsePacket(
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

	private static Component buildComponentFromSegments(List<TextSegment> segments) {
		if (segments == null || segments.isEmpty()) {
			return Component.empty();
		}

		MutableComponent root = Component.empty();

		for (TextSegment seg : segments) {
			MutableComponent part = Component.literal(seg.text);
			Style style = Style.EMPTY;

			// Apply color
			if (seg.color != null && !seg.color.isEmpty()) {
				TextColor textColor = TextColor.parseColor(seg.color).result().orElse(null);
				if (textColor != null) {
					style = style.withColor(textColor);
				}
			}

			// Apply formatting
			if (seg.bold) {
				style = style.withBold(true);
			}
			if (seg.italic) {
				style = style.withItalic(true);
			}
			if (seg.underlined) {
				style = style.withUnderlined(true);
			}
			if (seg.strikethrough) {
				style = style.withStrikethrough(true);
			}
			if (seg.obfuscated) {
				style = style.withObfuscated(true);
			}

			// Apply click event (open URL)
			if (seg.clickUrl != null && !seg.clickUrl.isEmpty()) {
				try {
					style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(seg.clickUrl)));
				} catch (Exception ignored) {
					// Invalid URL, skip click event
				}
			}

			// Apply hover text
			if (seg.hoverText != null && !seg.hoverText.isEmpty()) {
				style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(seg.hoverText)));
			}

			part.withStyle(style);
			root.append(part);
		}

		return root;
	}

	private static Component buildComponentFromSegmentsReplacingPlaceholder(List<TextSegment> segments,
	                                                                        String placeholder,
	                                                                        Component replacement) {
		if (segments == null || segments.isEmpty()) {
			return replacement;
		}

		MutableComponent root = Component.empty();
		for (TextSegment segment : segments) {
			if (segment.text == null || segment.text.isEmpty() || !segment.text.contains(placeholder)) {
				root.append(buildComponentPart(segment));
				continue;
			}

			int cursor = 0;
			while (cursor < segment.text.length()) {
				int placeholderStart = segment.text.indexOf(placeholder, cursor);
				if (placeholderStart < 0) {
					String tail = segment.text.substring(cursor);
					if (!tail.isEmpty()) {
						root.append(buildComponentPart(copySegmentWithText(segment, tail)));
					}
					break;
				}

				if (placeholderStart > cursor) {
					String leading = segment.text.substring(cursor, placeholderStart);
					root.append(buildComponentPart(copySegmentWithText(segment, leading)));
				}

				root.append(replacement.copy());
				cursor = placeholderStart + placeholder.length();
			}
		}

		return root;
	}

	private static TextSegment copySegmentWithText(TextSegment source, String text) {
		TextSegment copy = new TextSegment(text);
		copy.color = source.color;
		copy.bold = source.bold;
		copy.italic = source.italic;
		copy.underlined = source.underlined;
		copy.strikethrough = source.strikethrough;
		copy.obfuscated = source.obfuscated;
		copy.clickUrl = source.clickUrl;
		copy.hoverText = source.hoverText;
		return copy;
	}

	private static MutableComponent buildComponentPart(TextSegment segment) {
		MutableComponent part = Component.literal(segment.text == null ? "" : segment.text);
		Style style = Style.EMPTY;

		if (segment.color != null && !segment.color.isEmpty()) {
			TextColor textColor = TextColor.parseColor(segment.color).result().orElse(null);
			if (textColor != null) {
				style = style.withColor(textColor);
			}
		}

		if (segment.bold) {
			style = style.withBold(true);
		}
		if (segment.italic) {
			style = style.withItalic(true);
		}
		if (segment.underlined) {
			style = style.withUnderlined(true);
		}
		if (segment.strikethrough) {
			style = style.withStrikethrough(true);
		}
		if (segment.obfuscated) {
			style = style.withObfuscated(true);
		}

		if (segment.clickUrl != null && !segment.clickUrl.isEmpty()) {
			try {
				style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(segment.clickUrl)));
			} catch (Exception ignored) {
			}
		}

		if (segment.hoverText != null && !segment.hoverText.isEmpty()) {
			style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(segment.hoverText)));
		}

		part.withStyle(style);
		return part;
	}

	private static String serializeComponent(Component component) {
		if (component == null || serverInstance == null) {
			return "";
		}
		try {
			return ComponentSerialization.CODEC
					.encodeStart(RegistryOps.create(JsonOps.INSTANCE, serverInstance.registryAccess()), component)
					.result()
					.map(Object::toString)
					.orElse("");
		} catch (Exception ignored) {
			return "";
		}
	}

	private static Component deserializeComponent(String json) {
		if (json == null || json.isBlank() || serverInstance == null) {
			return null;
		}
		try {
			return ComponentSerialization.CODEC
					.parse(RegistryOps.create(JsonOps.INSTANCE, serverInstance.registryAccess()), JsonParser.parseString(json))
					.result()
					.orElse(null);
		} catch (Exception ignored) {
			return null;
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
