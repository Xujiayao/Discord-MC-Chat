package com.xujiayao.discord_mc_chat.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.LocalCommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.LinkCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

import java.util.function.Function;

import static net.minecraft.commands.Commands.LEVEL_ADMINS;
import static net.minecraft.commands.Commands.LEVEL_GAMEMASTERS;
import static net.minecraft.commands.Commands.LEVEL_MODERATORS;
import static net.minecraft.commands.Commands.LEVEL_OWNERS;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MinecraftCommands {

	private MinecraftCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var help = sub("help", -1, MinecraftCommands::createSenderForSource);
		var info = sub("info", -1, MinecraftCommandSender::new);
		var reload = sub("reload", 4, MinecraftCommandSender::new);
		var stats = literal("stats")
				.requires(Commands.hasPermission(of("command_permission_levels.stats", -1)))
				.then(argument("type", IdentifierArgument.id())
						.suggests((_, builder) -> SharedSuggestionProvider.suggestResource(
								BuiltInRegistries.STAT_TYPE.keySet(), builder))
						.then(argument("stat", IdentifierArgument.id())
								.suggests((ctx, builder) -> {
									try {
										Identifier typeLoc = ctx.getArgument("type", Identifier.class);
										return BuiltInRegistries.STAT_TYPE.get(typeLoc)
												.map(holder -> SharedSuggestionProvider.suggestResource(holder.value().getRegistry().keySet(), builder))
												.orElseGet(builder::buildFuture);
									} catch (Exception ignored) {
										return builder.buildFuture();
									}
								})
								.executes(ctx -> {
									Identifier typeLoc = ctx.getArgument("type", Identifier.class);
									Identifier statLoc = ctx.getArgument("stat", Identifier.class);
									CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "stats", typeLoc.toString(), statLoc.toString());
									return 1;
								})));
		var link = sub("link", 0, MinecraftPlayerCommandSender::new);
		var unlink = sub("unlink", 0, MinecraftPlayerCommandSender::new);
		var update = sub("update", -1, MinecraftCommandSender::new);
		var root = literal("dmcc")
				.requires(Commands.hasPermission(of("command_permission_levels.help", -1)))
				.executes(help.getCommand());

		dispatcher.register(root
				.then(help)
				.then(info)
				.then(reload)
				.then(stats)
				.then(link)
				.then(unlink)
				.then(update));
	}

	private static LocalCommandSender createSenderForSource(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			return new MinecraftPlayerCommandSender(source);
		}
		return new MinecraftCommandSender(source);
	}

	/**
	 * Registers a DMCC subcommand that forwards to the command with the same name.
	 * <p>
	 * The required permission level is read from {@code command_permission_levels.<name>}.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> sub(String name, int defaultLevel,
	                                                              Function<CommandSourceStack, LocalCommandSender> senderFactory,
	                                                              String... args) {
		return literal(name)
				.requires(Commands.hasPermission(of("command_permission_levels." + name, defaultLevel)))
				.executes(ctx -> {
					CommandManager.execute(senderFactory.apply(ctx.getSource()), name, args);
					return 1;
				});
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

	/**
	 * Shared behavior of the command senders backed by a Minecraft {@link CommandSourceStack}.
	 */
	private interface SourceBackedSender extends LocalCommandSender {

		CommandSourceStack source();

		@Override
		default void reply(String message) {
			for (String line : message.split("\n")) {
				source().sendSuccess(() -> Component.literal(line), false);
			}
		}

		@Override
		default int getOpLevel() {
			// Probe from highest to lowest to determine the sender's actual permission level
			if (LEVEL_OWNERS.check(source().permissions())) {
				return 4;
			}
			if (LEVEL_ADMINS.check(source().permissions())) {
				return 3;
			}
			if (LEVEL_GAMEMASTERS.check(source().permissions())) {
				return 2;
			}
			if (LEVEL_MODERATORS.check(source().permissions())) {
				return 1;
			}
			return 0;
		}
	}

	private record MinecraftCommandSender(CommandSourceStack source) implements SourceBackedSender {
	}

	private record MinecraftPlayerCommandSender(CommandSourceStack source)
			implements SourceBackedSender, LinkCommand.PlayerContextProvider {

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
