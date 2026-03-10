package com.xujiayao.discord_mc_chat.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.LinkCommand;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;

import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers DMCC commands into Minecraft Brigadier dispatcher.
 *
 * @author Xujiayao
 */
public class MinecraftCommands {

	/**
	 * Registers /dmcc commands.
	 *
	 * @param dispatcher The command dispatcher
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = literal("dmcc")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.help", -1)))
				.executes(ctx -> {
					CommandManager.execute(createSenderForSource(ctx.getSource()), "help");
					return 1;
				});
		var help = literal("help")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.help", -1)))
				.executes(ctx -> {
					CommandManager.execute(createSenderForSource(ctx.getSource()), "help");
					return 1;
				});
		var info = literal("info")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.info", -1)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "info");
					return 1;
				});
		var reload = literal("reload")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.reload", 4)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "reload");
					return 1;
				});
		var stats = literal("stats")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.stats", -1)))
				.then(argument("type", ResourceLocationArgument.id())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
								BuiltInRegistries.STAT_TYPE.keySet(), builder))
						.then(argument("stat", ResourceLocationArgument.id())
								.suggests((ctx, builder) -> {
									try {
										ResourceLocation typeLoc = ctx.getArgument("type", ResourceLocation.class);
										Optional<Holder.Reference<StatType<?>>> optional = BuiltInRegistries.STAT_TYPE.get(typeLoc);

										if (optional.isPresent()) {
											return SharedSuggestionProvider.suggestResource(optional.get().value().getRegistry().keySet(), builder);
										}
									} catch (Exception ignored) {
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									ResourceLocation typeLoc = ctx.getArgument("type", ResourceLocation.class);
									ResourceLocation statLoc = ctx.getArgument("stat", ResourceLocation.class);
									CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "stats", typeLoc.toString(), statLoc.toString());
									return 1;
								})));
		var link = literal("link")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.link", 0)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftPlayerCommandSender(ctx.getSource()), "link");
					return 1;
				});
		var unlink = literal("unlink")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.unlink", 0)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftPlayerCommandSender(ctx.getSource()), "unlink");
					return 1;
				});

		dispatcher.register(root
				.then(help)
				.then(info)
				.then(reload)
				.then(stats)
				.then(link)
				.then(unlink));
	}

	/**
	 * Creates the appropriate command sender based on whether the source is a player or console.
	 * <p>
	 * Players get a {@link MinecraftPlayerCommandSender} which provides player context
	 * for commands like link/unlink. Console/command blocks get a plain {@link MinecraftCommandSender}.
	 *
	 * @param source The command source stack.
	 * @return The appropriate command sender.
	 */
	private static LocalCommandSender createSenderForSource(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			return new MinecraftPlayerCommandSender(source);
		}
		return new MinecraftCommandSender(source);
	}

	/**
	 * Command sender implementation for Minecraft command sources.
	 * <p>
	 * Resolves the OP level from the Minecraft permission system by probing
	 * permission levels from 4 down to 0.
	 *
	 * @author Xujiayao
	 */
	private record MinecraftCommandSender(CommandSourceStack source) implements LocalCommandSender {

		@Override
		public void reply(String message) {
			// TODO Ephemeral if false, visible to all admin if true
			// For each line in the message, send a separate chat message
			for (String line : message.split("\n")) {
				source.sendSuccess(() -> Component.literal(line), false);
			}
		}

		@Override
		public int getOpLevel() {
			// Probe from highest to lowest to determine the sender's actual permission level
			for (int level = 4; level >= 0; level--) {
				if (source.hasPermission(level)) {
					return level;
				}
			}
			return 0;
		}
	}

	/**
	 * Command sender implementation for Minecraft players, providing player context
	 * (UUID and name) for account linking commands.
	 * <p>
	 * If the command source is not a player (e.g., console or command block),
	 * the player context methods return null/empty values.
	 *
	 * @author Xujiayao
	 */
	private record MinecraftPlayerCommandSender(CommandSourceStack source)
			implements LocalCommandSender, LinkCommand.PlayerContextProvider {

		@Override
		public void reply(String message) {
			for (String line : message.split("\n")) {
				source.sendSuccess(() -> Component.literal(line), false);
			}
		}

		@Override
		public int getOpLevel() {
			for (int level = 4; level >= 0; level--) {
				if (source.hasPermission(level)) {
					return level;
				}
			}
			return 0;
		}

		@Override
		public String getPlayerUuid() {
			if (source.getEntity() instanceof ServerPlayer player) {
				return player.getStringUUID();
			}
			return null;
		}

		@Override
		public String getPlayerName() {
			if (source.getEntity() instanceof ServerPlayer player) {
				return player.getName().getString();
			}
			return null;
		}
	}
}
