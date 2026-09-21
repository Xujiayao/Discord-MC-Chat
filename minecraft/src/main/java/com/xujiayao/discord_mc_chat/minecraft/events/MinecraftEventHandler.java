package com.xujiayao.discord_mc_chat.minecraft.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.JsonOps;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.events.CoreEvents;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Info.ResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.RequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MinecraftEventHandler {

	private static final String DEFAULT_MENTION_STYLE = "title";

	// Callers are Netty event loop threads, so this bounded wait must stay short; on expiry the
	// late server-thread result is discarded and no suggestions are returned.
	private static final long AUTOCOMPLETE_TIMEOUT_MILLIS = 500L;

	// 49 polls one interval apart span the same five-second window as the previous
	// "for (int i = 0; i < 50; i++)" loop; one extra interval before the response is collected
	// bounds the total wait at 5.1 seconds.
	private static final long COMMAND_RESPONSE_POLL_INTERVAL_MILLIS = 100L;
	private static final int COMMAND_RESPONSE_MAX_POLLS = 49;

	private static MinecraftServer serverInstance;
	private static MinecraftServer opsServerCache;
	private static RegistryOps<JsonElement> opsCache;

	// Capacity: a single reusable scheduler thread. Invalidation: shut down once the Minecraft
	// server has stopped; reset to null so a subsequent server start builds a fresh executor.
	private static ScheduledExecutorService commandResponseTimeoutExecutor;

	private MinecraftEventHandler() {
	}

	/**
	 * Returns the lazily created bound polling scheduler. Cancelled tasks leave the queue
	 * immediately so a burst of console commands cannot pile up triggered tasks.
	 */
	private static synchronized ScheduledExecutorService commandResponseTimeoutExecutor() {
		if (commandResponseTimeoutExecutor == null) {
			ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
					1,
					ExecutorServiceUtils.newThreadFactory("DMCC-Command-Timeout"));
			executor.setRemoveOnCancelPolicy(true);
			commandResponseTimeoutExecutor = executor;
		}
		return commandResponseTimeoutExecutor;
	}

	// Shuts down the DMCC executors owned by this handler so no DMCC thread outlives the server.
	private static synchronized void shutdownExecutorHelpers() {
		if (commandResponseTimeoutExecutor != null) {
			commandResponseTimeoutExecutor.shutdownNow();
			commandResponseTimeoutExecutor = null;
		}
	}

	public static void init() {
		EventManager.register(MinecraftEvents.ServerStarted.class, event -> {
			serverInstance = event.minecraftServer();

			StatsCommand.setProvider(new StatsCommand.StatsProvider() {
				@Override
				public void saveAll() {
					event.minecraftServer().getPlayerList().saveAll();
				}

				@Override
				public Path getStatsDirectory() {
					return event.minecraftServer().getWorldPath(LevelResource.PLAYER_STATS_DIR);
				}

				@Override
				public String getPlayerName(UUID uuid) {
					return event.minecraftServer().services().nameToIdCache()
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
			});

			Map<String, String> placeholders = Map.of();
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STARTED, placeholders));

			// Must run after the SERVER_STARTED packet above
			TranslationManager.setServer(event.minecraftServer());
			TranslationManager.init();

			NetworkManager.registerInfoSupplier(() -> buildInfoResponse(event.minecraftServer()));
		});

		EventManager.register(MinecraftEvents.ServerStopping.class, _ -> {
			Map<String, String> placeholders = Map.of();
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STOPPING, placeholders));
		});

		EventManager.register(MinecraftEvents.ServerStopped.class, _ -> {
			// Blocks until shutdown is complete
			DMCC.shutdown();

			// No command can arrive anymore once the server is stopped
			shutdownExecutorHelpers();
		});

		EventManager.register(MinecraftEvents.PlayerJoin.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString()
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_JOIN, placeholders));

			// Account linking check: routed via network packet
			String playerUuid = event.serverPlayer().getStringUUID();
			String playerName = event.serverPlayer().getName().getString();
			NetworkManager.sendPacketToServer(new RequestPacket(playerUuid, playerName, true));
		});

		EventManager.register(MinecraftEvents.PlayerQuit.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString()
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_QUIT, placeholders));
		});

		EventManager.register(MinecraftEvents.PlayerDie.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString(),
					"death_message", TranslationManager.get(event.serverPlayer().getCombatTracker().getDeathMessage())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_DIE, placeholders));
		});

		EventManager.register(MinecraftEvents.PlayerAdvancement.class, event -> {
			DisplayInfo displayInfo = event.advancementHolder().value().display().orElse(null);
			if (displayInfo != null
					&& displayInfo.announceToChat()
					&& event.advancementProgress().isDone()
					&& event.serverPlayer().level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
				String type = switch (displayInfo.type()) {
					case TASK -> "task";
					case CHALLENGE -> "challenge";
					case GOAL -> "goal";
				};

				Map<String, String> placeholders = Map.of(
						"type", type,
						"player_name", event.serverPlayer().getName().getString(),
						"display_name", event.serverPlayer().getDisplayName().getString(),
						"title", TranslationManager.get(displayInfo.title()),
						"description", TranslationManager.get(displayInfo.description())
				);

				NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_ADVANCEMENT, placeholders));
			}
		});

		EventManager.register(MinecraftEvents.PlayerChangeGameMode.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString(),
					"mode", TranslationManager.get(event.gameType().getLongDisplayName())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_CHANGE_GAME_MODE, placeholders));
		});

		EventManager.register(MinecraftEvents.PlayerChat.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_uuid", event.serverPlayer().getStringUUID(),
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString(),
					"message", TranslationManager.get(event.playerChatMessage().decoratedContent())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_CHAT, placeholders));
		});

		EventManager.register(MinecraftEvents.PlayerCommand.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_uuid", event.serverPlayer().getStringUUID(),
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString(),
					"command", "/" + event.command()
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_COMMAND, placeholders));
		});

		EventManager.register(MinecraftEvents.SourceSay.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_uuid", event.commandContext().getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
					"player_name", event.commandContext().getSource().getTextName(),
					"display_name", event.commandContext().getSource().getDisplayName().getString(),
					"message", TranslationManager.get(event.playerChatMessage().decoratedContent())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_SAY, placeholders));
		});

		EventManager.register(MinecraftEvents.SourceTellRaw.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_uuid", event.commandContext().getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
					"player_name", event.commandContext().getSource().getTextName(),
					"display_name", event.commandContext().getSource().getDisplayName().getString(),
					"message", TranslationManager.get(event.component()),
					"component_json", serializeComponent(event.component())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_TELL_RAW, placeholders));
		});

		EventManager.register(MinecraftEvents.SourceMsg.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_uuid", event.commandContext().getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : "",
					"player_name", event.commandContext().getSource().getTextName(),
					"display_name", event.commandContext().getSource().getDisplayName().getString(),
					"message", TranslationManager.get(event.playerChatMessage().decoratedContent())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_MSG, placeholders));
		});

		EventManager.register(MinecraftEvents.SourceMe.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.commandContext().getSource().getTextName(),
					"display_name", event.commandContext().getSource().getDisplayName().getString(),
					"action", TranslationManager.get(event.playerChatMessage().decoratedContent())
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SOURCE_ME, placeholders));
		});

		EventManager.register(MinecraftEvents.CommandRegister.class, event -> {
			MinecraftCommands.register(event.dispatcher());
		});

		EventManager.register(MinecraftEvents.ReloadResources.class, _ -> {
			TranslationManager.init();
		});

		// ===== Core Events: Minecraft Command Execution Bridge =====

		EventManager.register(CoreEvents.MinecraftCommandExecutionEvent.class, event -> {
			if (serverInstance == null) {
				event.sender().reply(I18nManager.getDmccTranslation("commands.console.server_not_ready"));
				event.completionFuture().complete(null);
				return;
			}

			// Discord visitors (-1) default to OP 0
			int mcOp = Math.max(0, event.sender().getOpLevel());

			DmccRconConsoleSource rconConsoleSource = new DmccRconConsoleSource(serverInstance);
			CommandSourceStack source = buildCommandSource(serverInstance, rconConsoleSource, mcOp);

			// Must be dispatched to the main server thread to avoid concurrent modification, and the
			// completion future is only completed once all output has been sent to the sender.
			serverInstance.execute(() -> {
				try {
					serverInstance.getCommands().performPrefixedCommand(source, event.commandLine());
				} finally {
					if (!rconConsoleSource.getCommandResponse().isEmpty()) {
						event.sender().reply(rconConsoleSource.getCommandResponse());
						event.completionFuture().complete(null);
					} else {
						// No output yet: the command may produce it later (network calls, database
						// access, scheduled tasks)
						awaitCommandResponse(rconConsoleSource, COMMAND_RESPONSE_MAX_POLLS, event);
					}
				}
			});
		});

		EventManager.register(CoreEvents.MinecraftCommandAutoCompleteEvent.class, event -> {
			if (serverInstance == null) return;

			int mcOp = Math.max(0, event.opLevel());

			String rawInput = event.input() == null ? "" : event.input();

			// Brigadier parsing and completion read live dispatcher/source state, so they run on the
			// server thread and are awaited for a short bounded time; on timeout or failure no
			// suggestions are produced.
			List<String> computed = computeSuggestionsWithTimeout(rawInput, mcOp);
			if (!computed.isEmpty()) {
				event.suggestions().addAll(computed);
			}
		});

		// ===== Account Linking Response Events =====

		EventManager.register(CoreEvents.LinkCodeResponseEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				try {
					UUID uuid = UUID.fromString(event.playerUuid());
					ServerPlayer player = serverInstance.getPlayerList().getPlayer(uuid);
					if (player != null) {
						if (event.alreadyLinked()) {
							player.sendSystemMessage(buildAlreadyLinkedMessage(event.discordName()));
						} else if (event.code() != null) {
							player.sendSystemMessage(buildNotLinkedMessage(event.code()));
						}
					}
				} catch (Exception ignored) {
				}
			});
		});

		EventManager.register(CoreEvents.UnlinkResponseEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				try {
					UUID uuid = UUID.fromString(event.playerUuid());
					ServerPlayer player = serverInstance.getPlayerList().getPlayer(uuid);
					if (player != null) {
						if (event.success()) {
							player.sendSystemMessage(Component.literal(
									I18nManager.getDmccTranslation("commands.unlink.success_minecraft", event.discordName())));
						} else {
							player.sendSystemMessage(Component.literal(
									I18nManager.getDmccTranslation("commands.unlink.not_linked")));
						}
					}
				} catch (Exception ignored) {
				}
			});
		});

		// ===== OP Level Sync =====

		EventManager.register(CoreEvents.OpSyncEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				try {
					PlayerList playerList = serverInstance.getPlayerList();
					ServerOpList opList = playerList.getOps();

					Map<UUID, Integer> currentOpLevels = new HashMap<>();
					for (ServerOpListEntry entry : opList.getEntries()) {
						try {
							NameAndId user = entry.getUser();
							if (user == null) continue;
							// id() already is the UUID; the previous String round trip only added a
							// per-entry parse and a failure mode for non-canonical UUID strings
							UUID uuid = user.id();
							int level = entry.permissions().level().id();
							if (level >= 0) currentOpLevels.put(uuid, level);
						} catch (Exception ignored) {
						}
					}

					// Non-positive levels are never added, so the de-op loop below removes them
					Map<UUID, Integer> desiredOpLevels = new HashMap<>();
					for (Map.Entry<String, Integer> e : event.opLevels().entrySet()) {
						int level = e.getValue();
						if (level <= 0) continue;
						try {
							desiredOpLevels.put(UUID.fromString(e.getKey()), level);
						} catch (Exception ignored) {
						}
					}

					// Equal maps: skip both save and permission update
					if (currentOpLevels.equals(desiredOpLevels)) {
						return;
					}

					boolean changed = false;

					// De-op users that are opped but not desired
					for (ServerOpListEntry entry : new ArrayList<>(opList.getEntries())) {
						NameAndId user = entry.getUser();
						if (user == null) continue;
						try {
							UUID uuid = user.id();
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
							continue;
						}
						NameAndId nameAndId = nameAndIdOpt.get();
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

						// Online players only, and only when the OP list was modified
						for (ServerPlayer player : playerList.getPlayers()) {
							playerList.sendPlayerPermissionLevel(player);
						}
					}
				} catch (Exception ignored) {
				}
			});
		});

		EventManager.register(CoreEvents.DiscordChatMessageEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();
				broadcastReplyAndMain(playerList, event.replySegments(), event.segments());
				sendMentionNotifications(playerList, event.mentionNotificationText(), event.mentionNotificationStyle(),
						event.mentionEveryone(), event.mentionedPlayerUuids());
			});
		});

		EventManager.register(CoreEvents.DiscordCommandEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> broadcast(serverInstance.getPlayerList(), buildComponentFromSegments(event.segments())));
		});

		EventManager.register(CoreEvents.DiscordReactionEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();
				broadcastReplyAndMain(playerList, event.replySegments(), event.segments());
			});
		});

		EventManager.register(CoreEvents.DiscordEditEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();

				// Order matters: reply, then edit notification, then edited message content
				broadcastReplyAndMain(playerList, event.replySegments(), event.segments());

				if (event.editedMessageSegments() != null && !event.editedMessageSegments().isEmpty()) {
					broadcast(playerList, buildComponentFromSegments(event.editedMessageSegments()));
				}
			});
		});

		EventManager.register(CoreEvents.DiscordDeleteEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();
				broadcastReplyAndMain(playerList, event.replySegments(), event.segments());
			});
		});

		EventManager.register(CoreEvents.MinecraftRelayMessageEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();
				Component component;
				if (event.componentJson() != null && !event.componentJson().isBlank()) {
					Component nativeComponent = deserializeComponent(event.componentJson());
					if (nativeComponent != null && event.componentPlaceholder() != null && !event.componentPlaceholder().isBlank()) {
						component = buildComponentFromSegmentsReplacingPlaceholder(event.segments(), event.componentPlaceholder(), nativeComponent);
					} else
						component = Objects.requireNonNullElseGet(nativeComponent, () -> buildComponentFromSegments(event.segments()));
				} else {
					component = buildComponentFromSegments(event.segments());
				}

				broadcast(playerList, component);
				sendMentionNotifications(playerList, event.mentionNotificationText(), event.mentionNotificationStyle(),
						event.mentionEveryone(), event.mentionedPlayerUuids());
			});
		});
	}

	private static void broadcast(PlayerList playerList, Component component) {
		for (ServerPlayer player : playerList.getPlayers()) {
			player.sendSystemMessage(component);
		}
	}

	// Reply (when present) is sent before the main component
	private static void broadcastReplyAndMain(PlayerList playerList, List<TextSegment> replySegments, List<TextSegment> mainSegments) {
		if (replySegments != null && !replySegments.isEmpty()) {
			broadcast(playerList, buildComponentFromSegments(replySegments));
		}
		broadcast(playerList, buildComponentFromSegments(mainSegments));
	}

	// Null notificationText means no mention notification at all
	private static void sendMentionNotifications(PlayerList playerList, String notificationText, String style,
	                                             boolean everyone, List<String> mentionedPlayerUuids) {
		if (notificationText == null) {
			return;
		}

		Component notificationComponent = Component.literal(notificationText)
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

		if (everyone) {
			// @everyone/@here: notify ALL online players
			for (ServerPlayer player : playerList.getPlayers()) {
				sendMentionNotification(player, notificationComponent, style);
			}
		} else if (mentionedPlayerUuids != null && !mentionedPlayerUuids.isEmpty()) {
			// Direct/role mentions: notify specific players
			for (String uuidStr : mentionedPlayerUuids) {
				try {
					ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
					if (player != null) {
						sendMentionNotification(player, notificationComponent, style);
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	// The server is passed explicitly because auto-complete reaches this from the Minecraft
	// server thread, where the static instance field must not be read off-thread.
	private static CommandSourceStack buildCommandSource(MinecraftServer server, DmccRconConsoleSource rconConsoleSource, int mcOp) {
		return new CommandSourceStack(
				rconConsoleSource,
				Vec3.atLowerCornerOf(server.getRespawnData().pos()),
				Vec2.ZERO,
				server.findRespawnDimension(),
				LevelBasedPermissionSet.forLevel(PermissionLevel.byId(mcOp)),
				Component.literal("DMCC"),
				server
		);
	}

	// Never blocks the server thread. Polling runs on a named single-thread scheduler and each
	// round re-schedules itself, so the wait is bounded by
	// COMMAND_RESPONSE_POLL_INTERVAL_MILLIS * (maxAttempts + 1) and the completion future is
	// always completed exactly once. The countdown continues while output is empty and stops on
	// the first round that observes output, which is still collected one interval later.
	private static void awaitCommandResponse(DmccRconConsoleSource rconConsoleSource,
	                                         int maxAttempts,
	                                         CoreEvents.MinecraftCommandExecutionEvent event) {
		if (maxAttempts <= 0 || !rconConsoleSource.getCommandResponse().isEmpty()) {
			// Output already produced, or poll budget exhausted: wait one more interval before
			// collecting so a late first line is still included.
			commandResponseTimeoutExecutor().schedule(
					() -> {
						event.sender().reply(rconConsoleSource.getCommandResponse());
						event.completionFuture().complete(null);
					},
					COMMAND_RESPONSE_POLL_INTERVAL_MILLIS,
					TimeUnit.MILLISECONDS);
			return;
		}

		// Re-scheduling every round instead of looping keeps the executor free for other commands
		commandResponseTimeoutExecutor().schedule(
				() -> awaitCommandResponse(rconConsoleSource, maxAttempts - 1, event),
				COMMAND_RESPONSE_POLL_INTERVAL_MILLIS,
				TimeUnit.MILLISECONDS);
	}

	private static List<String> getSuggestionsForInput(MinecraftServer server, String input, CommandSourceStack source) throws Exception {
		ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(input, source);

		Suggestions suggestions = server.getCommands().getDispatcher()
				.getCompletionSuggestions(parse)
				.get(3, TimeUnit.SECONDS);

		boolean isRootToken = !input.contains(" ");
		Set<String> allowedRoot = new LinkedHashSet<>();
		for (CommandNode<CommandSourceStack> child : server.getCommands().getDispatcher().getRoot().getChildren()) {
			if (!child.getName().isEmpty() && child.canUse(source)) {
				allowedRoot.add(child.getName());
			}
		}

		List<String> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
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

	// Caller runs on a Netty event loop thread, so the wait is bounded by
	// AUTOCOMPLETE_TIMEOUT_MILLIS. On timeout or failure an empty list is returned and the late
	// server-thread result (if any) is discarded.
	private static List<String> computeSuggestionsWithTimeout(String rawInput, int mcOp) {
		MinecraftServer server = serverInstance;
		if (server == null) {
			return List.of();
		}

		// Already on the server thread (e.g. a command source that completes its own input):
		// compute directly, since submitting and then blocking would deadlock the server thread.
		if (server.isSameThread()) {
			try {
				return computeSuggestions(server, rawInput, buildCommandSource(server, new DmccRconConsoleSource(server), mcOp));
			} catch (Exception ignored) {
				return List.of();
			}
		}

		CompletableFuture<List<String>> future = server.submit(() ->
				computeSuggestions(server, rawInput, buildCommandSource(server, new DmccRconConsoleSource(server), mcOp)));

		try {
			return future.get(AUTOCOMPLETE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException ignored) {
			// Bounded wait expired; the suggestion response is sent empty, as before on failure
			future.cancel(true);
			return List.of();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			return List.of();
		} catch (ExecutionException ignored) {
			// computeSuggestions already catches its own exceptions; kept for safety
			return List.of();
		}
	}

	private static List<String> computeSuggestions(MinecraftServer server, String rawInput, CommandSourceStack source) {
		try {
			// One parse per distinct input string: the rawInput result is reused by the exact-path
			// check and the "<input> " result by the self-candidate probe.
			SuggestionsResult current = computeSuggestionsResult(server, rawInput, source);

			if (!rawInput.isBlank() && !isExactPath(rawInput, current)) {
				return current.suggestions();
			}
			if (rawInput.isBlank() || rawInput.endsWith(" ")) {
				return current.suggestions();
			}

			SuggestionsResult next = computeSuggestionsResult(server, rawInput + " ", source);

			// Priority:
			// 1) self (only if self is a valid candidate)
			// 2) suggestions for "<input> "
			// 3) current suggestions
			Set<String> added = new LinkedHashSet<>();
			List<String> result = new ArrayList<>();

			if (isSelfCandidate(server, rawInput, next.suggestions(), current, source)) {
				added.add(rawInput);
				result.add(rawInput);
			}

			for (String s : next.suggestions()) {
				if (added.add(s)) {
					result.add(s);
				}
			}

			for (String s : current.suggestions()) {
				if (added.add(s)) {
					result.add(s);
				}
			}

			return result;
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static SuggestionsResult computeSuggestionsResult(MinecraftServer server, String input, CommandSourceStack source) throws Exception {
		List<String> suggestions = getSuggestionsForInput(server, input, source);

		boolean isRootToken = !input.contains(" ");
		boolean exactPath = !isRootToken || isRootCommandToken(server, input, source);

		return new SuggestionsResult(suggestions, exactPath, isRootToken);
	}

	// Equivalent to the previous parse-based check, which required the parse context range to
	// reach the end of the input, an empty exception map and at least one consumed node: a
	// non-root input addresses an exact path only when it does not end in a trailing partial
	// token, and for a single token only when that token is empty or exactly a usable root
	// command name (the dispatcher skips a leading "/").
	private static boolean isExactPath(String input, SuggestionsResult current) {
		if (current.isRootToken()) {
			return current.exactPath();
		}
		return !input.endsWith(" ");
	}

	private static boolean isSelfCandidate(MinecraftServer server,
	                                       String rawInput,
	                                       List<String> nextResult,
	                                       SuggestionsResult current,
	                                       CommandSourceStack source) {
		// Fast path: already present in computed suggestions.
		if (nextResult.contains(rawInput) || current.suggestions().contains(rawInput)) {
			return true;
		}

		// Backspace test: input-1 suggesting rawInput makes rawInput a valid candidate. This re-parse
		// matches the previous implementation; it only runs on this rare path, so the common case
		// stays at two parses instead of three.
		if (rawInput.length() <= 1) {
			return false;
		}

		try {
			SuggestionsResult backspaced = computeSuggestionsResult(server,
					rawInput.substring(0, rawInput.length() - 1), source);
			return backspaced.suggestions().contains(rawInput);
		} catch (Exception ignored) {
			return false;
		}
	}

	private static boolean isRootCommandToken(MinecraftServer server, String input, CommandSourceStack source) {
		for (CommandNode<CommandSourceStack> child : server.getCommands().getDispatcher().getRoot().getChildren()) {
			if (!child.getName().isEmpty() && child.canUse(source) && child.getName().equals(input)) {
				return true;
			}
		}
		return false;
	}

	// The visible suggestions plus the flags needed to decide whether the input addresses a
	// command path exactly and whether it is a single token.
	private record SuggestionsResult(List<String> suggestions, boolean exactPath, boolean isRootToken) {
	}

	private static Component buildNotLinkedMessage(String code) {
		String command = "/link code: " + code;
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_1")))
				.append(buildClickable(command, new ClickEvent.CopyToClipboard(command), "linking.tooltip.click_to_copy"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_2")))
				.append(buildClickable("/dmcc link", new ClickEvent.SuggestCommand("/dmcc link"), "linking.tooltip.click_to_run"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_3")));
	}

	private static Component buildAlreadyLinkedMessage(String discordName) {
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_1", discordName)))
				.append(buildClickable("/dmcc unlink", new ClickEvent.SuggestCommand("/dmcc unlink"), "linking.tooltip.click_to_run"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_2")));
	}

	private static Component buildClickable(String text, ClickEvent clickEvent, String tooltipKey) {
		return Component.literal("[" + text + "]").withStyle(style -> style
				.withClickEvent(clickEvent)
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal(I18nManager.getDmccTranslation(tooltipKey))))
				.withColor(ChatFormatting.GREEN));
	}

	private static ResponsePacket buildInfoResponse(MinecraftServer server) {
		String serverName = "single_server".equals(ModeManager.getMode()) ? "Internal" : ConfigManager.getString("multi_server.server_name");
		String minecraftVersion = EnvironmentUtils.getMinecraftVersion();

		Map<String, Integer> playersAndLatencies = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			playersAndLatencies.put(player.getDisplayName().getString(), player.connection.latency());
		}

		int onlinePlayers = playersAndLatencies.size();
		int maxPlayers = server.getPlayerList().getMaxPlayers();

		int playersEverJoined = StatsCommand.countStatResultEntries("minecraft:custom", "minecraft:play_time");

		double mspt = ((double) server.getAverageTickTimeNanos()) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
		ServerTickRateManager manager = server.tickRateManager();
		// The 1.0 MSPT lower bound keeps the division finite when the tick-rate manager reports a
		// zero milliseconds-per-tick during sprinting before any tick was measured (previously
		// +Infinity); any positive value is unchanged.
		double tps = 1000.0D / Math.max(manager.isSprinting() ? 0.0 : manager.millisecondsPerTick(), Math.max(1.0D, mspt));
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
			root.append(buildComponentPart(seg));
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
						root.append(buildComponentPart(segment.copyWithText(tail)));
					}
					break;
				}

				if (placeholderStart > cursor) {
					String leading = segment.text.substring(cursor, placeholderStart);
					root.append(buildComponentPart(segment.copyWithText(leading)));
				}

				root.append(replacement.copy());
				cursor = placeholderStart + placeholder.length();
			}
		}

		return root;
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
					.encodeStart(registryOps(), component)
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
					.parse(registryOps(), JsonParser.parseString(json))
					.result()
					.orElse(null);
		} catch (Exception ignored) {
			return null;
		}
	}

	// Cached per server: rebuilt only when serverInstance changes
	private static RegistryOps<JsonElement> registryOps() {
		if (opsCache == null || opsServerCache != serverInstance) {
			opsServerCache = serverInstance;
			opsCache = RegistryOps.create(JsonOps.INSTANCE, serverInstance.registryAccess());
		}
		return opsCache;
	}

	private static void sendMentionNotification(ServerPlayer player, Component component, String style) {
		if (style == null) {
			style = DEFAULT_MENTION_STYLE;
		}

		switch (style) {
			case "action_bar" -> player.connection.send(new ClientboundSetActionBarTextPacket(component));
			case "chat" -> player.sendSystemMessage(component);
			default -> {
				// Any unrecognized style falls back to title display
				player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
				player.connection.send(new ClientboundSetTitleTextPacket(component));
				// No subtitle packet is sent: the subtitle was always Component.empty(), and the client
				// renders a null subtitle and an empty subtitle identically while setTitle() still
				// resets the title timer.
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
