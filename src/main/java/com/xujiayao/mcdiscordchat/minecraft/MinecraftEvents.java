package com.xujiayao.mcdiscordchat.minecraft;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Optional;

/**
 * @author Xujiayao
 */
public interface MinecraftEvents {

	Event<PlayerMessage> PLAYER_MESSAGE = EventFactory.createArrayBacked(PlayerMessage.class, callbacks -> (player, playerChatMessage) -> {
		Optional<Component> result = Optional.empty();
		for (PlayerMessage callback : callbacks) {
			result = callback.message(player, playerChatMessage);
		}
		return result;
	});

	Event<PlayerAdvancement> PLAYER_ADVANCEMENT = EventFactory.createArrayBacked(PlayerAdvancement.class, callbacks -> (player, advancementHolder, isDone) -> {
		for (PlayerAdvancement callback : callbacks) {
			callback.advancement(player, advancementHolder, isDone);
		}
	});

	Event<PlayerDie> PLAYER_DIE = EventFactory.createArrayBacked(PlayerDie.class, callbacks -> (player, source) -> {
		for (PlayerDie callback : callbacks) {
			callback.die(player, source);
		}
	});

	Event<PlayerJoin> PLAYER_JOIN = EventFactory.createArrayBacked(PlayerJoin.class, callbacks -> player -> {
		for (PlayerJoin callback : callbacks) {
			callback.join(player);
		}
	});

	Event<PlayerQuit> PLAYER_QUIT = EventFactory.createArrayBacked(PlayerQuit.class, callbacks -> player -> {
		for (PlayerQuit callback : callbacks) {
			callback.quit(player);
		}
	});

	interface PlayerMessage {
		Optional<Component> message(ServerPlayer player, PlayerChatMessage playerChatMessage);
	}

	interface PlayerAdvancement {
		void advancement(ServerPlayer player, AdvancementHolder advancementHolder, boolean isDone);
	}

	interface PlayerDie {
		void die(ServerPlayer player, DamageSource source);
	}

	interface PlayerJoin {
		void join(ServerPlayer player);
	}

	interface PlayerQuit {
		void quit(ServerPlayer player);
	}
}
