package com.xujiayao.discord_mc_chat.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.LinkCommand;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.stats.StatType;

import java.util.Optional;

import static net.minecraft.commands.Commands.LEVEL_ADMINS;
import static net.minecraft.commands.Commands.LEVEL_GAMEMASTERS;
import static net.minecraft.commands.Commands.LEVEL_MODERATORS;
import static net.minecraft.commands.Commands.LEVEL_OWNERS;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers DMCC commands into Minecraft Brigadier dispatcher.
 *
 * @author Xujiayao
 */
public final class MinecraftCommands {

	private MinecraftCommands() {
	}

	/**
	 * Registers /dmcc commands.
	 *
	 * @param dispatcher The command dispatcher
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = literal("dmcc")
				.requires(Commands.hasPermission(of("command_permission_levels.help", -1)))
				.executes(ctx -> {
					CommandManager.execute(createSenderForSource(ctx.getSource()), "help");
					return 1;
				});
		var help = literal("help")
				.requires(Commands.hasPermission(of("command_permission_levels.help", -1)))
				.executes(ctx -> {
					CommandManager.execute(createSenderForSource(ctx.getSource()), "help");
					return 1;
				});
		var info = literal("info")
				.requires(Commands.hasPermission(of("command_permission_levels.info", -1)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "info");
					return 1;
				});
		var reload = literal("reload")
				.requires(Commands.hasPermission(of("command_permission_levels.reload", 4)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "reload");
					return 1;
				});
		var stats = literal("stats")
				.requires(Commands.hasPermission(of("command_permission_levels.stats", -1)))
				.then(argument("type", IdentifierArgument.id())
						.suggests((_, builder) -> SharedSuggestionProvider.suggestResource(
								BuiltInRegistries.STAT_TYPE.keySet(), builder))
						.then(argument("stat", IdentifierArgument.id())
								.suggests((ctx, builder) -> {
									try {
										Identifier typeLoc = ctx.getArgument("type", Identifier.class);
										Optional<Holder.Reference<StatType<?>>> optional = BuiltInRegistries.STAT_TYPE.get(typeLoc);

										if (optional.isPresent()) {
											return SharedSuggestionProvider.suggestResource(optional.get().value().getRegistry().keySet(), builder);
										}
									} catch (Exception ignored) {
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									Identifier typeLoc = ctx.getArgument("type", Identifier.class);
									Identifier statLoc = ctx.getArgument("stat", Identifier.class);
									CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "stats", typeLoc.toString(), statLoc.toString());
									return 1;
								})));
		var link = literal("link")
				.requires(Commands.hasPermission(of("command_permission_levels.link", 0)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftPlayerCommandSender(ctx.getSource()), "link");
					return 1;
				});
		var unlink = literal("unlink")
				.requires(Commands.hasPermission(of("command_permission_levels.unlink", 0)))
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

	private static LocalCommandSender createSenderForSource(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			return new MinecraftPlayerCommandSender(source);
		}
		return new MinecraftCommandSender(source);
	}

	private static PermissionCheck of(String configPath, int defaultLevel) {
		int opLevel = ConfigManager.getInt(configPath, defaultLevel);
		return switch (opLevel) {
			case 4 -> Commands.LEVEL_OWNERS;
			case 3 -> Commands.LEVEL_ADMINS;
			case 2 -> Commands.LEVEL_GAMEMASTERS;
			case 1 -> Commands.LEVEL_MODERATORS;
			default -> Commands.LEVEL_ALL;
		};
	}

	private record MinecraftCommandSender(CommandSourceStack source) implements LocalCommandSender {

		@Override
		public void reply(String message) {
			// For each line in the message, send a separate chat message
			for (String line : message.split("\n")) {
				source.sendSuccess(() -> Component.literal(line), false);
			}
		}

		@Override
		public int getOpLevel() {
			// Probe from highest to lowest to determine the sender's actual permission level
			if (LEVEL_OWNERS.check(source.permissions())) {
				return 4;
			}
			if (LEVEL_ADMINS.check(source.permissions())) {
				return 3;
			}
			if (LEVEL_GAMEMASTERS.check(source.permissions())) {
				return 2;
			}
			if (LEVEL_MODERATORS.check(source.permissions())) {
				return 1;
			}
			return 0;
		}
	}

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
			if (LEVEL_OWNERS.check(source.permissions())) {
				return 4;
			}
			if (LEVEL_ADMINS.check(source.permissions())) {
				return 3;
			}
			if (LEVEL_GAMEMASTERS.check(source.permissions())) {
				return 2;
			}
			if (LEVEL_MODERATORS.check(source.permissions())) {
				return 1;
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
