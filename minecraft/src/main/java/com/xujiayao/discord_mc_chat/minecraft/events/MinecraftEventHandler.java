package com.xujiayao.discord_mc_chat.minecraft.events;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.commands.MinecraftCommands;
import com.xujiayao.discord_mc_chat.minecraft.translations.TranslationManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.InfoResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.MinecraftEventPacket;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles Minecraft events posted from the event manager.
 *
 * @author Xujiayao
 */
public class MinecraftEventHandler {

	/**
	 * Initializes the Minecraft event handlers.
	 */
	public static void init() {
		EventManager.register(MinecraftEvents.ServerStarted.class, event -> {
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
	}

	private static InfoResponsePacket buildInfoResponse(MinecraftServer server) {
		String serverName = ConfigManager.getString("multi_server.server_name", "Internal");
		String minecraftVersion = EnvironmentUtils.getMinecraftVersion();

		int onlinePlayers = server.getPlayerList().getPlayerCount();
		int maxPlayers = server.getPlayerList().getMaxPlayers();

		Map<String, Integer> playersAndLatencies = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			playersAndLatencies.put(player.getDisplayName().getString(), 0);
		}

		double mspt = server.tickRateManager().millisecondsPerTick();
		double tps = server.tickRateManager().tickrate();

		long uptimeSeconds = server.getTickCount() / 20L;

		Runtime runtime = Runtime.getRuntime();
		return new InfoResponsePacket(
				serverName,
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
