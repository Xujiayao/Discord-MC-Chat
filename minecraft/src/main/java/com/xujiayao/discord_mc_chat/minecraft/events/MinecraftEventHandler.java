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
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.concurrent.TimeUnit;

/**
 * Handles Minecraft events posted from the event manager.
 *
 * @author Xujiayao
 */
public class MinecraftEventHandler {

	private static MinecraftServer serverInstance;

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

		EventManager.register(MinecraftEvents.ServerStopping.class, event -> {
			Map<String, String> placeholders = Map.of();
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.SERVER_STOPPING, placeholders));
		});

		EventManager.register(MinecraftEvents.ServerStopped.class, event -> {
			// Shutdown DMCC when the server is stopped
			// Blocks until shutdown is complete
			DMCC.shutdown();
		});

		EventManager.register(MinecraftEvents.PlayerJoin.class, event -> {
			Map<String, String> placeholders = Map.of(
					"user_name", event.serverPlayer().getName().getString(),
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
					"user_name", event.serverPlayer().getName().getString(),
					"display_name", event.serverPlayer().getDisplayName().getString()
			);
			NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_QUIT, placeholders));
		});

		EventManager.register(MinecraftEvents.PlayerDie.class, event -> {
			Map<String, String> placeholders = Map.of(
					"user_name", event.serverPlayer().getName().getString(),
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
						"user_name", event.serverPlayer().getName().getString(),
						"display_name", event.serverPlayer().getDisplayName().getString(),
						"title", TranslationManager.get(displayInfo.getTitle()),
						"description", TranslationManager.get(displayInfo.getDescription())
				);

				NetworkManager.sendPacketToServer(new MinecraftEventPacket(MinecraftEventPacket.MessageType.PLAYER_ADVANCEMENT, placeholders));
			}
		});

		EventManager.register(MinecraftEvents.CommandRegister.class, event -> {
			// Register Minecraft /dmcc commands
			MinecraftCommands.register(event.dispatcher());
		});

		EventManager.register(MinecraftEvents.ReloadResources.class, event -> {
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
			CommandSourceStack source = new CommandSourceStack(
					new CommandSource() {
						@Override
						public void sendSystemMessage(Component component) {
							event.sender().reply(TranslationManager.get(component));
						}

						@Override
						public boolean acceptsSuccess() {
							return true;
						}

						@Override
						public boolean acceptsFailure() {
							return true;
						}

						@Override
						public boolean shouldInformAdmins() {
							return true; // We want to relay all outputs
						}
					},
					Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
					Vec2.ZERO, serverInstance.findRespawnDimension(),
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
					event.completionFuture().complete(null);
				}
			});
		});

		EventManager.register(CoreEvents.MinecraftCommandAutoCompleteEvent.class, event -> {
			if (serverInstance == null) return;

			int mcOp = Math.max(0, event.opLevel());

			CommandSourceStack source = new CommandSourceStack(
					new CommandSource() {
						@Override
						public void sendSystemMessage(Component component) {
							// Not needed for auto-complete
						}

						@Override
						public boolean acceptsSuccess() {
							return true;
						}

						@Override
						public boolean acceptsFailure() {
							return true;
						}

						@Override
						public boolean shouldInformAdmins() {
							return true; // We want to relay all outputs
						}
					},
					Vec3.atLowerCornerOf(serverInstance.getRespawnData().pos()),
					Vec2.ZERO, serverInstance.findRespawnDimension(),
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

		// ===== Discord Chat Message =====

		EventManager.register(CoreEvents.DiscordChatEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				DiscordEventPacket p = event.packet();
				Component message = buildDiscordChatComponent(p);

				for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
					player.sendSystemMessage(message);
				}

				// Handle mention notifications
				if (p.mentionedMinecraftUuids != null && !p.mentionedMinecraftUuids.isEmpty()) {
					handleMentionNotifications(p);
				}
			});
		});

		// ===== Discord Command Notification =====

		EventManager.register(CoreEvents.DiscordCommandNotificationEvent.class, event -> {
			if (serverInstance == null) return;

			serverInstance.execute(() -> {
				DiscordEventPacket p = event.packet();
				Component message = buildDiscordCommandComponent(p);

				for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
					player.sendSystemMessage(message);
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
	 * @param command     The command to suggest (fill into chat input).
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
	 * Builds a Minecraft Component for a Discord chat message using the
	 * {@code discord_to_minecraft.chat} and {@code discord_to_minecraft.response}
	 * custom message templates.
	 * <p>
	 * If the message is a reply, the response template is rendered first (on a separate line)
	 * followed by the main chat template.
	 *
	 * @param packet The Discord event packet with parsed message data.
	 * @return A rich Minecraft Component ready to be sent to players.
	 */
	private static Component buildDiscordChatComponent(DiscordEventPacket packet) {
		MutableComponent result = Component.empty();

		// If this is a reply, render the response template first
		if (packet.hasReply && packet.replySegments != null) {
			List<Map<String, Object>> responseTemplate = I18nManager.getCustomMessageTemplate("discord_to_minecraft.response");
			if (responseTemplate != null) {
				result.append(buildFromTemplate(responseTemplate,
						packet.replyEffectiveName, packet.replyRoleColor, packet.replySegments, null));
				result.append(Component.literal("\n"));
			}
		}

		// Render the main chat template
		List<Map<String, Object>> chatTemplate = I18nManager.getCustomMessageTemplate("discord_to_minecraft.chat");
		if (chatTemplate != null) {
			result.append(buildFromTemplate(chatTemplate,
					packet.effectiveName, packet.roleColor, packet.segments, null));
		}

		return result;
	}

	/**
	 * Builds a Minecraft Component for a Discord command execution notification
	 * using the {@code discord_to_minecraft.command} custom message template.
	 *
	 * @param packet The Discord event packet with command notification data.
	 * @return A rich Minecraft Component ready to be sent to players.
	 */
	private static Component buildDiscordCommandComponent(DiscordEventPacket packet) {
		List<Map<String, Object>> commandTemplate = I18nManager.getCustomMessageTemplate("discord_to_minecraft.command");
		if (commandTemplate != null) {
			return buildFromTemplate(commandTemplate,
					packet.effectiveName, packet.roleColor, null, packet.commandName);
		}
		return Component.literal("[Discord] " + packet.effectiveName + " executed [" + packet.commandName + "] command!");
	}

	/**
	 * Sends mention notifications to Minecraft players
	 * whose linked Discord accounts were mentioned in the Discord message.
	 * <p>
	 * The notification style is controlled by
	 * {@code account_linking.discord_mention_notifications.style} (action_bar, title, or chat).
	 *
	 * @param packet The Discord event packet containing mention data.
	 */
	private static void handleMentionNotifications(DiscordEventPacket packet) {
		if (serverInstance == null || packet.mentionedMinecraftUuids == null) return;

		// Check if mention notifications are enabled
		Boolean mentionEnabled = ConfigManager.getBoolean("account_linking.discord_mention_notifications.enable");
		if (mentionEnabled == null || !mentionEnabled) return;

		String mentionTemplate = I18nManager.getCustomMessage("discord_to_minecraft.mentioned");
		if (mentionTemplate.isEmpty()) return;

		String mentionText = mentionTemplate.replace("{effective_name}", packet.effectiveName);
		Component mentionComponent = Component.literal(mentionText).withStyle(style -> style.withColor(ChatFormatting.GOLD));

		String notificationStyle = ConfigManager.getString("account_linking.discord_mention_notifications.style", "action_bar");

		for (String uuidStr : packet.mentionedMinecraftUuids) {
			try {
				UUID uuid = UUID.fromString(uuidStr);
				ServerPlayer player = serverInstance.getPlayerList().getPlayer(uuid);
				if (player != null) {
					switch (notificationStyle) {
						case "title" -> {
							// Send as title text (displayed prominently in the center of the screen)
							player.connection.send(new ClientboundSetTitleTextPacket(mentionComponent));
							player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
						}
						case "chat" -> player.sendSystemMessage(mentionComponent);
						default -> player.displayClientMessage(mentionComponent, true); // action_bar
					}
				}
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Builds a Minecraft Component from a custom messages template array.
	 * <p>
	 * Each element in the template array defines a text segment with optional formatting.
	 * Supports the following placeholders:
	 * <ul>
	 *   <li>{@code {effective_name}} - The Discord user's display name</li>
	 *   <li>{@code {role_color}} - The hex color of the user's top role</li>
	 *   <li>{@code {message}} - Replaced with the pre-parsed TextSegments from the packet</li>
	 *   <li>{@code {command}} - The slash command name (for command notifications)</li>
	 * </ul>
	 *
	 * @param template      The template array from custom_messages, as a list of maps.
	 * @param effectiveName The Discord user's display name.
	 * @param roleColor     The hex color of the user's top role (e.g. "#FF0000"), or null.
	 * @param segments      The pre-parsed message segments (null for command templates).
	 * @param commandName   The command name (null for chat templates).
	 * @return The built Minecraft Component.
	 */
	private static Component buildFromTemplate(List<Map<String, Object>> template,
											   String effectiveName,
											   String roleColor,
											   List<DiscordEventPacket.TextSegment> segments,
											   String commandName) {
		MutableComponent result = Component.empty();

		for (Map<String, Object> segmentMap : template) {
			String text = (String) segmentMap.getOrDefault("text", "");
			boolean bold = Boolean.TRUE.equals(segmentMap.get("bold"));
			boolean italic = Boolean.TRUE.equals(segmentMap.get("italic"));
			boolean underlined = Boolean.TRUE.equals(segmentMap.get("underlined"));
			boolean strikethrough = Boolean.TRUE.equals(segmentMap.get("strikethrough"));
			String color = (String) segmentMap.get("color");

			// Replace {role_color} in color field
			if (color != null && color.contains("{role_color}")) {
				color = roleColor != null ? roleColor : "white";
			}

			// Check if this segment contains the {message} placeholder
			if (text.contains("{message}") && segments != null) {
				String[] parts = text.split("\\{message}", -1);

				// Text before {message}
				if (!parts[0].isEmpty()) {
					String before = parts[0].replace("{effective_name}", effectiveName != null ? effectiveName : "Unknown");
					if (commandName != null) before = before.replace("{command}", commandName);
					result.append(buildStyledLiteral(before, color, bold, italic, underlined, strikethrough));
				}

				// Render each TextSegment from the packet
				for (DiscordEventPacket.TextSegment seg : segments) {
					result.append(buildTextSegmentComponent(seg, color));
				}

				// Text after {message}
				if (parts.length > 1 && !parts[1].isEmpty()) {
					String after = parts[1].replace("{effective_name}", effectiveName != null ? effectiveName : "Unknown");
					if (commandName != null) after = after.replace("{command}", commandName);
					result.append(buildStyledLiteral(after, color, bold, italic, underlined, strikethrough));
				}
			} else {
				// Regular template segment - replace all placeholders
				text = text.replace("{effective_name}", effectiveName != null ? effectiveName : "Unknown");
				if (commandName != null) text = text.replace("{command}", commandName);
				result.append(buildStyledLiteral(text, color, bold, italic, underlined, strikethrough));
			}
		}

		return result;
	}

	/**
	 * Builds a styled Minecraft Component from a plain text string with formatting parameters.
	 *
	 * @param text          The text content.
	 * @param color         The color string (hex like "#FF0000" or named like "blue"), or null.
	 * @param bold          Whether the text is bold.
	 * @param italic        Whether the text is italic.
	 * @param underlined    Whether the text is underlined.
	 * @param strikethrough Whether the text has strikethrough.
	 * @return The styled Component.
	 */
	private static Component buildStyledLiteral(String text, String color,
												boolean bold, boolean italic,
												boolean underlined, boolean strikethrough) {
		MutableComponent comp = Component.literal(text);
		return comp.withStyle(style -> {
			if (color != null) style = style.withColor(parseTextColor(color));
			if (bold) style = style.withBold(true);
			if (italic) style = style.withItalic(true);
			if (underlined) style = style.withUnderlined(true);
			if (strikethrough) style = style.withStrikethrough(true);
			return style;
		});
	}

	/**
	 * Builds a Minecraft Component from a single pre-parsed {@link DiscordEventPacket.TextSegment}.
	 * <p>
	 * The segment's own color takes precedence; if null, the template default color is used.
	 * Click and hover events from the segment are applied if present.
	 *
	 * @param seg          The text segment from the Discord event packet.
	 * @param defaultColor The fallback color from the template (may be null).
	 * @return The styled Minecraft Component.
	 */
	private static Component buildTextSegmentComponent(DiscordEventPacket.TextSegment seg, String defaultColor) {
		MutableComponent comp = Component.literal(seg.text);
		return comp.withStyle(style -> {
			// Use segment's own color, or fallback to template default
			String segColor = seg.color != null ? seg.color : defaultColor;
			if (segColor != null) style = style.withColor(parseTextColor(segColor));

			if (seg.bold) style = style.withBold(true);
			if (seg.italic) style = style.withItalic(true);
			if (seg.underlined) style = style.withUnderlined(true);
			if (seg.strikethrough) style = style.withStrikethrough(true);

			if (seg.clickUrl != null) {
				try {
					style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(seg.clickUrl)));
				} catch (Exception ignored) {
				}
			}

			if (seg.hoverText != null) {
				style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(seg.hoverText)));
			}

			return style;
		});
	}

	/**
	 * Parses a color string into a Minecraft TextColor integer.
	 * <p>
	 * Supports hex colors (e.g. "#FF0000") and named Minecraft colors (e.g. "blue", "dark_gray").
	 *
	 * @param color The color string.
	 * @return The TextColor integer value, or white (-1) if parsing fails.
	 */
	private static int parseTextColor(String color) {
		if (color == null) return ChatFormatting.WHITE.getColor();

		// Hex color
		if (color.startsWith("#")) {
			try {
				return Integer.parseInt(color.substring(1), 16);
			} catch (NumberFormatException e) {
				return ChatFormatting.WHITE.getColor();
			}
		}

		// Named Minecraft colors
		ChatFormatting formatting = ChatFormatting.getByName(color);
		if (formatting != null && formatting.getColor() != null) {
			return formatting.getColor();
		}

		return ChatFormatting.WHITE.getColor();
	}
}
