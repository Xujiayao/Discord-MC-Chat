package com.xujiayao.discord_mc_chat.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

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
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.help", 0)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "help");
					return 1;
				});
		var help = literal("help")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.help", 0)))
				.executes(ctx -> {
					CommandManager.execute(new MinecraftCommandSender(ctx.getSource()), "help");
					return 1;
				});
		var info = literal("info")
				.requires(source -> source.hasPermission(ConfigManager.getInt("command_permission_levels.info", 0)))
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

		dispatcher.register(root
				.then(help)
				.then(info)
				.then(reload));
	}

	/**
	 * Command sender implementation for Minecraft command sources.
	 *
	 * @author Xujiayao
	 */
	private record MinecraftCommandSender(CommandSourceStack source) implements CommandSender {

		@Override
		public void reply(String message) {
			// TODO Ephemeral if false, visible to all admin if true
			// For each line in the message, send a separate chat message
			for (String line : message.split("\n")) {
				source.sendSuccess(() -> Component.literal(line), false);
			}
		}
	}
}
