package com.xujiayao.discord_mc_chat.minecraft;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * @author Xujiayao
 */
public interface MinecraftEvents {

	Event<CommandMessage> COMMAND_MESSAGE = EventFactory.createArrayBacked(CommandMessage.class, callbacks -> (message, commandSourceStack) -> {
		for (CommandMessage callback : callbacks) {
			callback.message(message, commandSourceStack);
		}
	});

	Event<PlayerMessage> PLAYER_MESSAGE = EventFactory.createArrayBacked(PlayerMessage.class, callbacks -> (player, message) -> {
		Optional<Component> result = Optional.empty();
		for (PlayerMessage callback : callbacks) {
			result = callback.message(player, message);
		}
		return result;
	});

	Event<PlayerCommand> PLAYER_COMMAND = EventFactory.createArrayBacked(PlayerCommand.class, callbacks -> (player, command) -> {
		for (PlayerCommand callback : callbacks) {
			callback.command(player, command);
		}
	});

	Event<PlayerAdvancement> PLAYER_ADVANCEMENT = EventFactory.createArrayBacked(PlayerAdvancement.class, callbacks -> (player, advancementHolder, isDone) -> {
		for (PlayerAdvancement callback : callbacks) {
			callback.advancement(player, advancementHolder, isDone);
		}
	});

	Event<PlayerDie> PLAYER_DIE = EventFactory.createArrayBacked(PlayerDie.class, callbacks -> player -> {
		for (PlayerDie callback : callbacks) {
			callback.die(player);
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

	interface CommandMessage {
		void message(String message, CommandSourceStack commandSourceStack);
	}

	interface PlayerMessage {
		Optional<Component> message(ServerPlayer player, String message);
	}

	interface PlayerCommand {
		void command(ServerPlayer player, String command);
	}


	interface PlayerAdvancement {
		void advancement(ServerPlayer player, AdvancementHolder advancementHolder, boolean isDone);
	}

	interface PlayerDie {
		void die(ServerPlayer player);
	}

	interface PlayerJoin {
		void join(ServerPlayer player);
	}

	interface PlayerQuit {
		void quit(ServerPlayer player);
	}
}
