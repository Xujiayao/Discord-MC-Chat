package com.xujiayao.discord_mc_chat.minecraft.events;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.events.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.events.CoreEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
				event.sender().reply("Minecraft server is not ready yet.");
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
				} catch (Exception e) {
					event.sender().reply("Error executing command: " + e.getMessage());
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

			ParseResults<CommandSourceStack> parse = serverInstance.getCommands().getDispatcher().parse(event.input(), source);
			try {
				Suggestions suggestions = serverInstance.getCommands().getDispatcher().getCompletionSuggestions(parse).get(3, TimeUnit.SECONDS);
				for (Suggestion suggestion : suggestions.getList()) {
					event.suggestions().add(suggestion.apply(event.input()));
				}
			} catch (Exception ignored) {
			}
		});
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
