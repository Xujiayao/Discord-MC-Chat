package com.xujiayao.mcdiscordchat.minecraft;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * @author Xujiayao
 */
public interface MinecraftEvents {

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
