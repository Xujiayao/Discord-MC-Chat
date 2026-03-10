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
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkRequestPacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.OpSyncManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.StatType;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
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

			// Trigger initial OP sync after server is ready (single_server mode)
			if ("single_server".equals(ModeManager.getMode())) {
				OpSyncManager.syncAll();
			}
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

			// Account linking: check if this player is linked
			String playerUuid = event.serverPlayer().getStringUUID();
			String playerName = event.serverPlayer().getName().getString();

			switch (ModeManager.getMode()) {
				case "single_server" -> {
					// Direct access to server-side managers
					if (!LinkedAccountManager.isMinecraftUuidLinked(playerUuid)) {
						String code = VerificationCodeManager.generateOrRefreshCode(playerUuid, playerName);
						// Notify the player in-game
						ServerPlayer sp = event.serverPlayer();
						sp.sendSystemMessage(Component.literal(
								I18nManager.getDmccTranslation("linking.player_join.not_linked")));
						sp.sendSystemMessage(Component.literal(
								I18nManager.getDmccTranslation("linking.player_join.code_hint", code)));
					}
				}
				case "multi_server_client" -> {
					// Send request to standalone server
					NetworkManager.sendPacketToServer(new LinkRequestPacket(playerUuid, playerName));
				}
			}
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
							player.sendSystemMessage(Component.literal(
									I18nManager.getDmccTranslation("commands.link.already_linked", event.discordName())));
						} else if (event.code() != null) {
							player.sendSystemMessage(Component.literal(
									I18nManager.getDmccTranslation("linking.player_join.code_hint", event.code())));
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
									I18nManager.getDmccTranslation("commands.unlink.success", event.discordName())));
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
					net.minecraft.server.players.PlayerList playerList = serverInstance.getPlayerList();
					net.minecraft.server.players.ServerOpList opList = playerList.getOps();

					// Step 1: De-op all currently opped players
					// Copy the list to avoid ConcurrentModificationException
					List<net.minecraft.server.players.ServerOpListEntry> currentOps =
							new ArrayList<>(opList.getEntries());
					for (net.minecraft.server.players.ServerOpListEntry op : currentOps) {
						com.mojang.authlib.GameProfile profile = op.getUser();
						if (profile != null) {
							playerList.deop(profile);
						}
					}

					// Step 2: Apply the new OP levels from the sync event
					for (Map.Entry<String, Integer> entry : event.opLevels().entrySet()) {
						String uuidStr = entry.getKey();
						int opLevel = entry.getValue();
						if (opLevel <= 0) continue; // OP 0 means no special permissions, skip

						try {
							UUID uuid = UUID.fromString(uuidStr);
							com.mojang.authlib.GameProfile profile = serverInstance.getProfileCache()
									.get(uuid).orElse(null);
							if (profile == null) {
								// Profile not in cache; skip this entry as we can't op without a valid profile
								continue;
							}
							// Add the OP entry with the exact desired level
							opList.add(new net.minecraft.server.players.ServerOpListEntry(
									profile, opLevel, opList.canBypassPlayerLimit(profile)));
						} catch (Exception ignored) {
						}
					}

					// Step 3: Save the ops list
					try {
						opList.save();
					} catch (Exception ignored) {
					}

					// Step 4: Update permission levels for online players
					for (ServerPlayer player : playerList.getPlayers()) {
						playerList.sendPlayerPermissionLevel(player);
					}
				} catch (Exception ignored) {
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
}
