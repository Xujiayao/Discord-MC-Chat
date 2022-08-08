package top.xujiayao.mcdiscordchat.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
//#if MC < 11900
//$$ import net.minecraft.text.LiteralText;
//#endif
import net.minecraft.text.Text;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;

/**
 * @author Xujiayao
 */
public class MinecraftCommands {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("mcdc").executes(context -> {
					//#if MC >= 11900
					context.getSource().sendFeedback(Text.literal(MarkdownParser.parseMarkdown(
					//#else
					//$$ context.getSource().sendFeedback(new LiteralText(MarkdownParser.parseMarkdown(
					//#endif
							CONFIG.generic.useEngInsteadOfChin ? """
									=============== Help ===============
									/mcdc info                    | Query server running status
									/mcdc help                    | Get a list of available commands
									/mcdc update                  | Check for update
									/mcdc stats <type> <name>     | Query the scoreboard of a statistic
									/mcdc reload                  | Reload MCDiscordChat config file (admin only)
									~~/mcdc console <command>~~   | Execute a command in the server console (admin only)
									~~/mcdc log~~                 | Get the specified server log (admin only)
									~~/mcdc stop~~                | Stop the server (admin only)
									
									*Strikethrough commands are not implemented because they are redundant or impossible to implement in-game.*
									
									*Admin-only commands require a level 4 operator at minimum, i.e. players with OP permission level 4 or the server console.*""" : """
									=============== 帮助 ===============
									/mcdc info                    | 查询服务器运行状态
									/mcdc help                    | 获取可用命令列表
									/mcdc update                  | 检查更新
									/mcdc stats <type> <name>     | 查询该统计信息的排行榜
									/mcdc reload                  | 重新加载 MCDiscordChat 配置文件（仅限管理员）
									~~/mcdc console <command>~~   | 在服务器控制台中执行命令（仅限管理员）
									~~/mcdc log~~                 | 获取指定的服务器日志（仅限管理员）
									~~/mcdc stop~~                | 停止服务器（仅限管理员）
									
									*带删除线的命令并没有实现，因为这些命令在游戏内是多余或无法实现的。*
									
									*仅限管理员的命令仅对 4 级管理员可用，即拥有 OP 权限等级 4 的玩家或服务器控制台。*""")), false);
					return 1;
				})
				.then(literal("info").executes(context -> {
					//#if MC >= 11900
					context.getSource().sendFeedback(Text.literal(
					//#else
					//$$ context.getSource().sendFeedback(new LiteralText(
					//#endif
							Utils.getInfoCommandMessage()), false);
					return 1;
				}))
				.then(literal("help").executes(context -> {
					//#if MC >= 11900
					context.getSource().sendFeedback(Text.literal(MarkdownParser.parseMarkdown(
					//#else
					//$$ context.getSource().sendFeedback(new LiteralText(MarkdownParser.parseMarkdown(
					//#endif
							CONFIG.generic.useEngInsteadOfChin ? """
							=============== Help ===============
							/mcdc info                    | Query server running status
							/mcdc help                    | Get a list of available commands
							/mcdc update                  | Check for update
							/mcdc stats <type> <name>     | Query the scoreboard of a statistic
							/mcdc reload                  | Reload MCDiscordChat config file (admin only)
							~~/mcdc console <command>~~   | Execute a command in the server console (admin only)
							~~/mcdc log~~                 | Get the specified server log (admin only)
							~~/mcdc stop~~                | Stop the server (admin only)
							
							*Strikethrough commands are not implemented because they are redundant or impossible to implement in-game.*
							
							*Admin-only commands require a level 4 operator at minimum, i.e. players with OP permission level 4 or the server console.*""" : """
							=============== 帮助 ===============
							/mcdc info                    | 查询服务器运行状态
							/mcdc help                    | 获取可用命令列表
							/mcdc update                  | 检查更新
							/mcdc stats <type> <name>     | 查询该统计信息的排行榜
							/mcdc reload                  | 重新加载 MCDiscordChat 配置文件（仅限管理员）
							~~/mcdc console <command>~~   | 在服务器控制台中执行命令（仅限管理员）
							~~/mcdc log~~                 | 获取指定的服务器日志（仅限管理员）
							~~/mcdc stop~~                | 停止服务器（仅限管理员）
							
							*带删除线的命令并没有实现，因为这些命令在游戏内是多余或无法实现的。*
							
							*仅限管理员的命令仅对 4 级管理员可用，即拥有 OP 权限等级 4 的玩家或服务器控制台。*""")), false);
					return 1;
				}))
				.then(literal("update").executes(context -> {
					//#if MC >= 11900
					context.getSource().sendFeedback(Text.literal(
					//#else
					//$$ context.getSource().sendFeedback(new LiteralText(
					//#endif
							MarkdownParser.parseMarkdown(Utils.checkUpdate(true))), false);
					return 1;
				}))
				.then(literal("stats")
						.then(argument("type", StringArgumentType.word())
								.then(argument("name", StringArgumentType.word())
										.executes(context -> {
											String type = StringArgumentType.getString(context, "type");
											String name = StringArgumentType.getString(context, "name");
											//#if MC >= 11900
											context.getSource().sendFeedback(Text.literal(
											//#else
											//$$ context.getSource().sendFeedback(new LiteralText(
											//#endif
													Utils.getStatsCommandMessage(type, name)), false);
											return 1;
										}))))
				.then(literal("reload")
						.requires(source -> source.hasPermissionLevel(4))
						.executes(context -> {
							//#if MC >= 11900
							context.getSource().sendFeedback(Text.literal(
							//#else
							//$$ context.getSource().sendFeedback(new LiteralText(
							//#endif
									MarkdownParser.parseMarkdown(Utils.reload())), false);
							return 1;
						})));
	}
}
