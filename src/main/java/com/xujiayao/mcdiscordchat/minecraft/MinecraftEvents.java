package com.xujiayao.mcdiscordchat.minecraft;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * @author Xujiayao
 */
public interface MinecraftEvents {

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

	interface PlayerJoin {
		void join(ServerPlayer player);
	}

	interface PlayerQuit {
		void quit(ServerPlayer player);
	}
}
