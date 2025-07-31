package com.xujiayao.discord_mc_chat.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.xujiayao.discord_mc_chat.utils.MarkdownParser;
import com.xujiayao.discord_mc_chat.utils.Translations;
import com.xujiayao.discord_mc_chat.utils.Utils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
//#if MC < 11900
//$$ import net.minecraft.network.chat.TextComponent;
//#endif

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * @author Xujiayao
 */
public class MinecraftCommands {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("dmcc").executes(context -> {
				//#if MC > 11904
				context.getSource().sendSuccess(() ->
				//#else
				//$$ context.getSource().sendSuccess(
				//#endif
					//#if MC >= 11900
					Component.literal(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					//#else
					//$$ new TextComponent(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					//#endif
				return 1;
			})
			.then(literal("help").executes(context -> {
				//#if MC > 11904
				context.getSource().sendSuccess(() ->
				//#else
				//$$ context.getSource().sendSuccess(
				//#endif
					//#if MC >= 11900
					Component.literal(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					//#else
					//$$ new TextComponent(MarkdownParser.parseMarkdown(Utils.getHelpCommandMessage(false) + Translations.translate("minecraft.mcCommands.register.helpMessageExplanation"))), false);
					//#endif
				return 1;
			}))
			.then(literal("info").executes(context -> {
				//#if MC > 11904
				context.getSource().sendSuccess(() ->
				//#else
				//$$ context.getSource().sendSuccess(
				//#endif
					//#if MC >= 11900
					Component.literal(Utils.getInfoCommandMessage()), false);
					//#else
					//$$ new TextComponent(Utils.getInfoCommandMessage()), false);
					//#endif
				return 1;
			}))
			.then(literal("stats")
				.then(argument("type", StringArgumentType.word())
					.then(argument("name", StringArgumentType.word())
						.executes(context -> {
							String type = StringArgumentType.getString(context, "type");
							String name = StringArgumentType.getString(context, "name");
							//#if MC > 11904
							context.getSource().sendSuccess(() ->
							//#else
							//$$ context.getSource().sendSuccess(
							//#endif
								//#if MC >= 11900
								Component.literal(Utils.getStatsCommandMessage(type, name, false)), false);
								//#else
								//$$ new TextComponent(Utils.getStatsCommandMessage(type, name, false)), false);
								//#endif
							return 1;
						}))))
			.then(literal("update").executes(context -> {
				//#if MC > 11904
				context.getSource().sendSuccess(() ->
				//#else
				//$$ context.getSource().sendSuccess(
				//#endif
					//#if MC >= 11900
					Component.literal(MarkdownParser.parseMarkdown(Utils.checkUpdate(true))), false);
					//#else
					//$$ new TextComponent(MarkdownParser.parseMarkdown(Utils.checkUpdate(true))), false);
					//#endif
				return 1;
			}))
			.then(literal("whitelist")
				.requires(source -> source.hasPermission(CONFIG.generic.whitelistRequiresAdmin ? 4 : 0))
				.then(argument("player", StringArgumentType.word())
					.executes(context -> {
						String player = StringArgumentType.getString(context, "player");
						//#if MC > 11904
						context.getSource().sendSuccess(() ->
						//#else
						//$$ context.getSource().sendSuccess(
						//#endif
							//#if MC >= 11900
							Component.literal(MarkdownParser.parseMarkdown(Utils.whitelist(player))), false);
							//#else
							//$$ new TextComponent(MarkdownParser.parseMarkdown(Utils.whitelist(player))), false);
							//#endif
						return 1;
					})))
			.then(literal("reload")
				.requires(source -> source.hasPermission(4))
				.executes(context -> {
					//#if MC > 11904
					context.getSource().sendSuccess(() ->
					//#else
					//$$ context.getSource().sendSuccess(
					//#endif
						//#if MC >= 11900
						Component.literal(MarkdownParser.parseMarkdown(Utils.reload())), false);
						//#else
						//$$ new TextComponent(MarkdownParser.parseMarkdown(Utils.reload())), false);
						//#endif
					return 1;
				})));
	}
}
