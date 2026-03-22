package com.xujiayao.discord_mc_chat.minecraft.events;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.info.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkRequestPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.TextSegment;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatType;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.level.GameRules;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles Minecraft events posted from the event manager.
 *
 * @author Xujiayao
 */
public final class MinecraftEventHandler {

	private static final String DEFAULT_MENTION_STYLE = "title";
	private static MinecraftServer serverInstance;

	private MinecraftEventHandler() {
	}

	/**
	 * Initializes the Minecraft event handlers.
	 */
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
					for (ResourceLocation loc : BuiltInRegistries.STAT_TYPE.keySet()) {
						types.add(loc.toString());
					}
					return types;
				}

				@Override
				public List<String> getStatNames(String typeStr) {
					List<String> stats = new ArrayList<>();
					try {
						ResourceLocation typeLoc = ResourceLocation.parse(typeStr);
						Optional<Holder.Reference<StatType<?>>> optional = BuiltInRegistries.STAT_TYPE.get(typeLoc);
						if (optional.isPresent()) {
							for (ResourceLocation loc : optional.get().value().getRegistry().keySet()) {
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

			// Initialize translation manager with the started server instance after announcing server started event
			TranslationManager.setServer(event.minecraftServer());
			TranslationManager.init();

			// Register info supplier after server is started
			NetworkManager.registerInfoSupplier(() -> buildInfoResponse(event.minecraftServer()));
		});

		EventManager.register(MinecraftEvents.ServerStopping.class, _ -> {
			Map<String, String> placeholders = Map.of();
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STOPPING, placeholders));
		});

		EventManager.register(MinecraftEvents.ServerStopped.class, _ -> {
			// Shutdown DMCC when the server is stopped
			// Blocks until shutdown is complete
			DMCC.shutdown();
		});

		EventManager.register(MinecraftEvents.PlayerJoin.class, event -> {
			Map<String, String> placeholders = Map.of(
					"player_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString()
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_JOIN, placeholders));

			// Account linking: check if this player is linked (via network packet)
			String playerUuid = event.serverPlayer().getStringUUID();
			String playerName = event.serverPlayer().getName().getString();
			NetworkManager.sendPacketToServer(new LinkRequestPacket(playerUuid, playerName, true));
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
					&& displayInfo.shouldAnnounceChat()
					&& event.advancementProgress().isDone()
					&& event.serverPlayer().level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
				String type = switch (displayInfo.getType()) {
					case TASK -> "task";
					case CHALLENGE -> "challenge";
					case GOAL -> "goal";
				};

				Map<String, String> placeholders = Map.of(
						"type", type,
						"player_name", event.serverPlayer().getName().getString(),
						"display_name", event.serverPlayer().getDisplayName().getString(),
						"title", TranslationManager.get(displayInfo.getTitle()),
						"description", TranslationManager.get(displayInfo.getDescription())
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
			// TODO Rewrite
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
			// Register Minecraft /dmcc commands
			MinecraftCommands.register(event.dispatcher());
		});

		EventManager.register(MinecraftEvents.ReloadResources.class, _ -> {
			// Refresh Minecraft translations
			TranslationManager.init();
		});

		// ===== Core Events: Minecraft Command Execution Bridge =====

		EventManager.register(CoreEvents.MinecraftCommandExecutionEvent.class, event -> {
			if (serverInstance == null) {
				event.sender().reply(I18nManager.getDmccTranslation("commands.console.server_not_ready"));
				event.completionFuture().complete(null);
				return;
			}

			// Discord visitors (-1) default to OP 0 for Minecraft's permission system
			int mcOp = Math.max(0, event.sender().getOpLevel());

			// Construct a virtual CommandSourceStack with the sender's OP level
			// and a custom CommandSource that bridges output back to the DMCC sender
			RconConsoleSource rconConsoleSource = new RconConsoleSource(serverInstance);
			CommandSourceStack source = new CommandSourceStack(
					rconConsoleSource,
					Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
					Vec2.ZERO,
					serverInstance.findRespawnDimension(),
					mcOp,
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
					serverInstance.getCommands().performPrefixedCommand(source, event.commandLine());
				} finally {
					// First check if there's an immediate response
					// (for simple commands that execute synchronously and produce output right away)
					if (!rconConsoleSource.getCommandResponse().isEmpty()) {
						event.sender().reply(rconConsoleSource.getCommandResponse());
						event.completionFuture().complete(null);
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
							event.sender().reply(rconConsoleSource.getCommandResponse());
							event.completionFuture().complete(null);
						});
					}
				}
			});
		});

		EventManager.register(CoreEvents.MinecraftCommandAutoCompleteEvent.class, event -> {
			if (serverInstance == null) return;

			int mcOp = Math.max(0, event.opLevel());

			CommandSourceStack source = new CommandSourceStack(
					new RconConsoleSource(serverInstance),
					Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
					Vec2.ZERO,
					serverInstance.findRespawnDimension(),
					mcOp,
					"DMCC",
					Component.literal("DMCC"),
					serverInstance,
					null
			);

			String rawInput = event.input() == null ? "" : event.input();

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
						event.suggestions().add(rawInput);
					}

					for (String s : nextResult) {
						if (added.add(s)) {
							event.suggestions().add(s);
						}
					}

					for (String s : currentResult) {
						if (added.add(s)) {
							event.suggestions().add(s);
						}
					}
					return;
				}

				event.suggestions().addAll(currentResult);
			} catch (Exception ignored) {
			}
		});

		// ===== Account Linking Response Events =====

		EventManager.register(CoreEvents.LinkCodeResponseEvent.class, event -> {
			if (serverInstance == null) return;

			// Find the player and notify them
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

					// Build a map of current OP levels: UUID -> level
					Map<UUID, Integer> currentOpLevels = new HashMap<>();
					for (ServerOpListEntry entry : opList.getEntries()) {
						try {
							NameAndId user = entry.getUser();
							if (user == null) continue;
							UUID uuid = UUID.fromString(user.id().toString());
							// Read level from entry
							int level = entry.getLevel();
							if (level >= 0) currentOpLevels.put(uuid, level);
						} catch (Exception ignored) {
						}
					}

					// Build desired OP levels map from the event (UUID -> level), skipping non-positive levels
					Map<UUID, Integer> desiredOpLevels = new HashMap<>();
					for (Map.Entry<String, Integer> e : event.opLevels().entrySet()) {
						int level = e.getValue();
						if (level <= 0) continue;
						try {
							desiredOpLevels.put(UUID.fromString(e.getKey()), level);
						} catch (Exception ignored) {
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
						opList.add(new ServerOpListEntry(nameAndId, level, opList.canBypassPlayerLimit(nameAndId)));
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
		});

		EventManager.register(CoreEvents.DiscordChatMessageEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();

				// Build and broadcast the reply line first (if present)
				if (event.replySegments() != null && !event.replySegments().isEmpty()) {
					Component replyComponent = buildComponentFromSegments(event.replySegments());
					for (ServerPlayer player : playerList.getPlayers()) {
						player.sendSystemMessage(replyComponent);
					}
				}

				// Build and broadcast the main message line
				Component mainComponent = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(mainComponent);
				}

				// Send mention notifications
				if (event.mentionNotificationText() != null) {
					Component notificationComponent = Component.literal(event.mentionNotificationText())
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

					if (event.mentionEveryone()) {
						// @everyone/@here: notify ALL online players
						for (ServerPlayer player : playerList.getPlayers()) {
							sendMentionNotification(player, notificationComponent, event.mentionNotificationStyle());
						}
					} else if (event.mentionedPlayerUuids() != null && !event.mentionedPlayerUuids().isEmpty()) {
						// Direct/role mentions: notify specific players
						for (String uuidStr : event.mentionedPlayerUuids()) {
							try {
								ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
								if (player != null) {
									sendMentionNotification(player, notificationComponent, event.mentionNotificationStyle());
								}
							} catch (Exception ignored) {
							}
						}
					}
				}
			});
		});

		EventManager.register(CoreEvents.DiscordCommandEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				Component component = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
					player.sendSystemMessage(component);
				}
			});
		});

		EventManager.register(CoreEvents.DiscordReactionEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();

				if (event.replySegments() != null && !event.replySegments().isEmpty()) {
					Component replyComponent = buildComponentFromSegments(event.replySegments());
					for (ServerPlayer player : playerList.getPlayers()) {
						player.sendSystemMessage(replyComponent);
					}
				}

				Component component = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(component);
				}
			});
		});

		EventManager.register(CoreEvents.DiscordEditEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();

				if (event.replySegments() != null && !event.replySegments().isEmpty()) {
					Component replyComponent = buildComponentFromSegments(event.replySegments());
					for (ServerPlayer player : playerList.getPlayers()) {
						player.sendSystemMessage(replyComponent);
					}
				}

				// Send edit notification
				Component notificationComponent = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(notificationComponent);
				}

				// Send edited message content
				if (event.editedMessageSegments() != null && !event.editedMessageSegments().isEmpty()) {
					Component editedComponent = buildComponentFromSegments(event.editedMessageSegments());
					for (ServerPlayer player : playerList.getPlayers()) {
						player.sendSystemMessage(editedComponent);
					}
				}
			});
		});

		EventManager.register(CoreEvents.DiscordDeleteEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();

				if (event.replySegments() != null && !event.replySegments().isEmpty()) {
					Component replyComponent = buildComponentFromSegments(event.replySegments());
					for (ServerPlayer player : playerList.getPlayers()) {
						player.sendSystemMessage(replyComponent);
					}
				}

				Component component = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(component);
				}
			});
		});

		EventManager.register(CoreEvents.MinecraftRelayMessageEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				PlayerList playerList = serverInstance.getPlayerList();
				Component component = buildComponentFromSegments(event.segments());
				for (ServerPlayer player : playerList.getPlayers()) {
					player.sendSystemMessage(component);
				}

				if (event.mentionNotificationText() != null) {
					Component notificationComponent = Component.literal(event.mentionNotificationText())
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
					if (event.mentionEveryone()) {
						for (ServerPlayer player : playerList.getPlayers()) {
							sendMentionNotification(player, notificationComponent, event.mentionNotificationStyle());
						}
					} else if (event.mentionedPlayerUuids() != null && !event.mentionedPlayerUuids().isEmpty()) {
						for (String uuidStr : event.mentionedPlayerUuids()) {
							try {
								ServerPlayer player = playerList.getPlayer(UUID.fromString(uuidStr));
								if (player != null) {
									sendMentionNotification(player, notificationComponent, event.mentionNotificationStyle());
								}
							} catch (Exception ignored) {
							}
						}
					}
				}
			});
		});
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

	/**
	 * Determines whether the raw input itself should be included as the "self" suggestion.
	 * This avoids showing invalid partial tokens such as "dmcc stats minecraft:cust".
	 *
	 * @param rawInput      The original user input.
	 * @param source        The command source stack for permission context.
	 * @param nextResult    The suggestions for "<input> " (if applicable).
	 * @param currentResult The suggestions for the current input.
	 * @return true if rawInput is a valid candidate to be included as a suggestion, false otherwise.
	 */
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

	/**
	 * Builds a rich Component for the "not linked" notification shown on both player join
	 * and /dmcc link when the account is not linked.
	 * Contains inline clickable elements: [/link code: CODE] for copy-to-clipboard
	 * and [/dmcc link] for suggest-command.
	 *
	 * @param code The verification code.
	 * @return A rich Component with inline clickable elements.
	 */
	private static Component buildNotLinkedMessage(String code) {
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_1")))
				.append(buildCopyToClipboard("/link code: " + code))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_2")))
				.append(buildSuggestCommand("/dmcc link"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.not_linked_3")));
	}

	/**
	 * Builds a rich Component for the "already linked" response from /dmcc link.
	 * Contains an inline clickable [/dmcc unlink] suggest-command element.
	 *
	 * @param discordName The Discord user's display name.
	 * @return A rich Component with inline clickable element.
	 */
	private static Component buildAlreadyLinkedMessage(String discordName) {
		return Component.empty()
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_1", discordName)))
				.append(buildSuggestCommand("/dmcc unlink"))
				.append(Component.literal(I18nManager.getDmccTranslation("linking.message.already_linked_2")));
	}

	/**
	 * Builds a clickable Component that copies the given text to clipboard when clicked.
	 * Displayed in green with brackets.
	 *
	 * @param text The text to copy and display.
	 * @return A clickable Component.
	 */
	private static Component buildCopyToClipboard(String text) {
		return Component.literal("[" + text + "]").withStyle(style -> style
				.withClickEvent(new ClickEvent.CopyToClipboard(text))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal(I18nManager.getDmccTranslation("linking.tooltip.click_to_copy"))))
				.withColor(ChatFormatting.GREEN));
	}

	/**
	 * Builds a clickable Component that suggests a command (fills the chat input) when clicked.
	 * Displayed in green with brackets.
	 *
	 * @param command The command to suggest (fill into chat input).
	 * @return A clickable Component.
	 */
	private static Component buildSuggestCommand(String command) {
		return Component.literal("[" + command + "]").withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal(I18nManager.getDmccTranslation("linking.tooltip.click_to_run"))))
				.withColor(ChatFormatting.GREEN));
	}

	/**
	 * Builds an InfoResponsePacket containing real-time metrics of the Minecraft server.
	 *
	 * @param server The Minecraft server instance.
	 * @return The populated InfoResponsePacket.
	 */
	private static InfoResponsePacket buildInfoResponse(MinecraftServer server) {
		String serverName = "single_server".equals(ModeManager.getMode()) ? "Internal" : ConfigManager.getString("multi_server.server_name");
		String minecraftVersion = EnvironmentUtils.getMinecraftVersion();

		int onlinePlayers = server.getPlayerList().getPlayerCount();
		int maxPlayers = server.getPlayerList().getMaxPlayers();

		Map<String, Integer> playersAndLatencies = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			playersAndLatencies.put(player.getDisplayName().getString(), player.connection.latency());
		}

		double mspt = ((double) server.getAverageTickTimeNanos()) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
		ServerTickRateManager manager = server.tickRateManager();
		double tps = 1000.0D / Math.max(manager.isSprinting() ? 0.0 : manager.millisecondsPerTick(), mspt);
		if (manager.isFrozen()) {
			tps = 0;
		}

		long uptimeSeconds = TimeUnit.MILLISECONDS.toSeconds(ManagementFactory.getRuntimeMXBean().getUptime());

		Runtime runtime = Runtime.getRuntime();
		return new InfoResponsePacket(
				serverName,
				-1,
				minecraftVersion,
				onlinePlayers,
				maxPlayers,
				playersAndLatencies,
				tps,
				mspt,
				uptimeSeconds,
				runtime.totalMemory(),
				runtime.freeMemory()
		);
	}

	/**
	 * Converts a list of {@link TextSegment} objects into a Minecraft {@link Component}.
	 * <p>
	 * Each segment is converted into a styled literal component with the appropriate
	 * color, formatting, click events, and hover text as specified by the segment.
	 *
	 * @param segments The text segments to convert.
	 * @return A composite Minecraft Component.
	 */
	private static Component buildComponentFromSegments(List<TextSegment> segments) {
		MutableComponent root = Component.empty();

		for (TextSegment seg : segments) {
			MutableComponent part = Component.literal(seg.text);
			Style style = Style.EMPTY;

			// Apply color
			if (seg.color != null && !seg.color.isEmpty()) {
				TextColor textColor = parseTextColor(seg.color);
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

	/**
	 * Parses a color string into a Minecraft {@link TextColor}.
	 * <p>
	 * Supports named Minecraft colors (e.g. "gold", "dark_gray") and hex color codes
	 * (e.g. "#3366CC").
	 *
	 * @param colorStr The color string.
	 * @return The parsed TextColor, or null if the string is invalid.
	 */
	private static TextColor parseTextColor(String colorStr) {
		if (colorStr.startsWith("#")) {
			try {
				return TextColor.parseColor(colorStr).result().orElse(null);
			} catch (Exception e) {
				return null;
			}
		}

		// Try named color via ChatFormatting
		ChatFormatting formatting = ChatFormatting.getByName(colorStr);
		if (formatting != null && formatting.isColor()) {
			return TextColor.fromLegacyFormat(formatting);
		}

		// Try parsing as hex anyway
		try {
			return TextColor.parseColor(colorStr).result().orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Sends a mention notification to a player using the configured style.
	 *
	 * @param player    The target player.
	 * @param component The notification component.
	 * @param style     The notification style: "action_bar", "title", or "chat".
	 */
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

		player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0F, 2.0F);
	}
}
