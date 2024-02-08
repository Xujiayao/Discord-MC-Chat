package com.xujiayao.mcdiscordchat.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.xujiayao.mcdiscordchat.utils.MarkdownParser;
import com.xujiayao.mcdiscordchat.utils.Translations;
import com.xujiayao.mcdiscordchat.utils.Utils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.xujiayao.mcdiscordchat.Main.CONFIG;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * @author Xujiayao
 */
public class MinecraftCommands {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("mcdc").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					return 1;
				})
				.then(literal("help").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					return 1;
				}))
				.then(literal("info").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(Utils.getInfoCommandMessage()), false);
					return 1;
				}))
				.then(literal("stats")
						.then(argument("type", StringArgumentType.word())
								.then(argument("name", StringArgumentType.word())
										.executes(context -> {
											String type = StringArgumentType.getString(context, "type");
											String name = StringArgumentType.getString(context, "name");
											context.getSource().sendSuccess(() -> Component.literal(Utils.getStatsCommandMessage(type, name)), false);
											return 1;
										}))))
				.then(literal("update").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(MarkdownParser.parseMarkdown(Utils.checkUpdate(true))), false);
					return 1;
				}))
				.then(literal("whitelist")
						.requires(source -> source.hasPermission(CONFIG.generic.whitelistRequiresAdmin ? 4 : 0))
						.then(argument("player", StringArgumentType.word())
								.executes(context -> {
									String player = StringArgumentType.getString(context, "player");
									context.getSource().sendSuccess(() -> Component.literal(MarkdownParser.parseMarkdown(Utils.whitelist(player))), false);
									return 1;
								})))
				.then(literal("reload")
						.requires(source -> source.hasPermission(4))
						.executes(context -> {
							context.getSource().sendSuccess(() -> Component.literal(MarkdownParser.parseMarkdown(Utils.reload())), false);
							return 1;
						})));
	}
}
