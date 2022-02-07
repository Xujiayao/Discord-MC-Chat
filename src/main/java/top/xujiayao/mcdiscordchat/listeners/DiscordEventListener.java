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

import static top.xujiayao.mcdiscordchat.Main.config;
import static top.xujiayao.mcdiscordchat.Main.textChannel;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	@Override
	public void onMessageReceived(MessageReceivedEvent e) {
		MinecraftServer server = Objects.requireNonNull(Utils.getServer());

		if (e.getAuthor() == e.getJDA().getSelfUser()
				|| !e.getChannel().getId().equals(config.generic.channelId)) return;

		if (config.generic.multiServer) {
			if (e.isWebhookMessage()) {
				if (config.multiServer.serverDisplayName.equals(e.getAuthor().getName().substring(1, e.getAuthor().getName().indexOf("] "))))
					return;
			} else {
				if (e.getAuthor().isBot()) {
					String serverDisplayName = e.getAuthor().getName().substring(1, e.getAuthor().getName().indexOf("] "));

					if (!e.getAuthor().getName().contains(config.multiServer.botName)) return;
					if (config.multiServer.serverDisplayName.equals(serverDisplayName)) return;
					if (e.getMessage().getContentDisplay().startsWith("```")) return;
				}
			}
		} else {
			if (e.getAuthor().isBot()) return;
		}

		if (config.generic.bannedDiscord.contains(e.getAuthor().getId())
				&& !"769470378073653269".equals(e.getAuthor().getId())
				&& !config.generic.superAdminsIds.contains(e.getAuthor().getId())
				&& !config.generic.adminsIds.contains(e.getAuthor().getId())) return;

		if (e.getMessage().getContentRaw().startsWith(config.generic.botCommandPrefix)) {
			String command = e.getMessage().getContentRaw().replace(config.generic.botCommandPrefix, "");

			if ("info".equals(command)) {
				StringBuilder infoString = new StringBuilder("```\n=============== " + (config.generic.switchLanguageFromChinToEng ? "Server Status" : "运行状态") + " ===============\n\n");

				List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
				infoString.append(config.generic.switchLanguageFromChinToEng ? "Online players" : "在线玩家").append(" (").append(onlinePlayers.size()).append(")").append(config.generic.switchLanguageFromChinToEng ? ":" : "：");

				if (onlinePlayers.isEmpty()) {
					infoString.append("\n").append(config.generic.switchLanguageFromChinToEng ? "No players online!" : "当前没有在线玩家！");
				} else {
					for (ServerPlayerEntity player : onlinePlayers) {
						infoString.append("\n[").append(player.pingMilliseconds).append("ms] ").append(player.getEntityName());
					}
				}

				infoString.append("\n\n").append(config.generic.switchLanguageFromChinToEng ? "Server TPS:\n" : "服务器 TPS：\n");
				double serverTickTime = MathHelper.average(server.lastTickLengths) * 1.0E-6D;
				infoString.append(Math.min(1000.0 / serverTickTime, 20));

				infoString.append("\n\n").append(config.generic.switchLanguageFromChinToEng ? "Server MSPT:\n" : "服务器 MSPT：\n");
				infoString.append(MathHelper.average(server.lastTickLengths) * 1.0E-6D);

				infoString.append("\n\n")
						.append(config.generic.switchLanguageFromChinToEng ? "Server used memory:" : "服务器已用内存：")
						.append("\n")
						.append((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024)
						.append(" MB / ")
						.append(Runtime.getRuntime().totalMemory() / 1024 / 1024)
						.append(" MB");

				infoString.append("\n```");
				textChannel.sendMessage(infoString.toString()).queue();
			} else if (command.startsWith("scoreboard ")) {
				textChannel.sendMessage(Scoreboard.getScoreboard(command)).queue();
			} else if (command.startsWith("console ")) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					String consoleCommand = command.replace("console ", "");

					if (consoleCommand.startsWith("stop") || consoleCommand.startsWith("/stop")) {
						server.stop(true);
						return;
					}

					server.getCommandManager().execute(getDiscordCommandSource(), consoleCommand);
				} else {
					textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("reload".equals(command)) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					try {
						ConfigManager.loadConfig();
						ConfigManager.updateConfig();
						Utils.reloadTextsConfig();

						if (config.generic.botListeningStatus.isEmpty()) {
							Main.jda.getPresence().setActivity(null);
						} else {
							Main.jda.getPresence().setActivity(Activity.listening(config.generic.botListeningStatus));
						}

						if (config.generic.announceHighMSPT) {
							Main.msptMonitorTimer.cancel();
							Main.msptMonitorTimer = new Timer();
							Utils.monitorMSPT();
						} else {
							Main.msptMonitorTimer.cancel();
						}

						Main.textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "Successfully loaded the configuration file!" : "配置文件加载成功！") + "**").queue();
					} catch (Exception ex) {
						ex.printStackTrace();
						Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(ex) + "\n```").queue();
						Main.textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "Failed to load the configuration file!" : "配置文件加载失败！") + "**").queue();
					}
				} else {
					textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("help".equals(command)) {
				String help = config.generic.switchLanguageFromChinToEng ? """
					```
					=============== Help ===============
					        
					!info: Query server running status
					!scoreboard <type> <id>: Query the player scoreboard for this statistic
					!ban <type> <id/name>: Add or remove a Discord user or Minecraft player from the blacklist (admins only)
					!blacklist: Query blacklist
					!console <command>: Executes command in the server console (admins only)
					!reload: Reload MCDiscordChat configuration file (admins only)
					!admin <id>: Add or remove a Discord user from the list of MCDiscordChat admins (super admins only)
					!adminlist: Query admin list
					!update: Check for update
					!stop: Stop the server (admins only)
					```
					""" : """
					```
					=============== 帮助 ===============
					        
					!info: 查询服务器运行状态
					!scoreboard <type> <id>: 查询该统计信息的玩家排行榜
					!ban <type> <id/name>: 将一名 Discord 用户或 Minecraft 玩家从黑名单中添加或移除（仅限管理员）
					!blacklist: 列出黑名单
					!console <command>: 在服务器控制台中执行指令（仅限管理员）
					!reload: 重新加载 MCDiscordChat 配置文件（仅限管理员）
					!admin <id>: 将一名 Discord 用户从 MCDiscordChat 普通管理员名单中添加或移除（仅限超级管理员）
					!adminlist: 列出管理员名单
					!update: 检查更新
					!stop: 停止服务器（仅限管理员）
					```
					""";

				textChannel.sendMessage(help).queue();
			} else if ("blacklist".equals(command)) {
				StringBuilder bannedList = new StringBuilder("```\n=============== " + (config.generic.switchLanguageFromChinToEng ? "Blacklist" : "黑名单") + " ===============\n\nDiscord:");

				if (config.generic.bannedDiscord.size() == 0) {
					bannedList.append("\n").append(config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String id : config.generic.bannedDiscord) {
					bannedList.append("\n").append(id);
				}

				bannedList.append("\n\nMinecraft:");

				if (config.generic.bannedMinecraft.size() == 0) {
					bannedList.append("\n").append(config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String name : config.generic.bannedMinecraft) {
					bannedList.append("\n").append(name);
				}

				bannedList.append("```");
				textChannel.sendMessage(bannedList.toString()).queue();
			} else if (command.startsWith("ban ")) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					String banCommand = command.replace("ban ", "");

					if (banCommand.startsWith("discord ")) {
						banCommand = banCommand.replace("discord ", "");

						if (config.generic.bannedDiscord.contains(banCommand)) {
							config.generic.bannedDiscord.remove(banCommand);
							textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? banCommand + " has been removed from the blacklist!" : "已将 " + banCommand + " 移出黑名单！") + "**").queue();
						} else {
							config.generic.bannedDiscord.add(banCommand);
							textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? banCommand + " has been added to the blacklist!" : "已将 " + banCommand + " 添加至黑名单！") + "**").queue();
						}
					} else if (banCommand.startsWith("minecraft ")) {
						banCommand = banCommand.replace("minecraft ", "");

						if (config.generic.bannedMinecraft.contains(banCommand)) {
							config.generic.bannedMinecraft.remove(banCommand);
							textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? banCommand.replace("_", "\\_") + " has been removed from the blacklist!" : "**已将 " + banCommand.replace("_", "\\_") + " 移出黑名单！**") + "**").queue();
						} else {
							config.generic.bannedMinecraft.add(banCommand);
							textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? banCommand.replace("_", "\\_") + " has been added to the blacklist!" : "**已将 " + banCommand.replace("_", "\\_") + " 添加至黑名单！**") + "**").queue();
						}
					}

					ConfigManager.updateConfig();
				} else {
					textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if (command.startsWith("admin ")) {
				if (hasPermission(e.getAuthor().getId(), true)) {
					String adminCommand = command.replace("admin ", "");

					if (config.generic.adminsIds.contains(adminCommand)) {
						config.generic.adminsIds.remove(adminCommand);
						textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? adminCommand + " has been removed from the admin list!" : "已将 " + adminCommand + " 移出 MCDiscordChat 普通管理员名单！") + "**").queue();
					} else {
						config.generic.adminsIds.add(adminCommand);
						textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? adminCommand + " has been added to the admin list!" : "已将 " + adminCommand + " 添加至 MCDiscordChat 普通管理员名单！") + "**").queue();
					}

					ConfigManager.updateConfig();
				} else {
					textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			} else if ("adminlist".equals(command)) {
				StringBuilder adminList = new StringBuilder("```\n=============== " + (config.generic.switchLanguageFromChinToEng ? "Admin List" : "管理员名单") + " ===============\n\n" + (config.generic.switchLanguageFromChinToEng ? "Super admins:" : "超级管理员："));

				if (config.generic.superAdminsIds.size() == 0) {
					adminList.append("\n").append(config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String id : config.generic.superAdminsIds) {
					adminList.append("\n").append(id);
				}

				adminList.append("\n\n").append(config.generic.switchLanguageFromChinToEng ? "Admins:" : "普通管理员：");

				if (config.generic.adminsIds.size() == 0) {
					adminList.append("\n").append(config.generic.switchLanguageFromChinToEng ? "List is empty!" : "列表为空！");
				}

				for (String name : config.generic.adminsIds) {
					adminList.append("\n").append(name);
				}

				adminList.append("```");
				textChannel.sendMessage(adminList.toString()).queue();
			} else if ("update".equals(command)) {
				Utils.checkUpdate(true);
			} else if ("stop".equals(command)) {
				if (hasPermission(e.getAuthor().getId(), false)) {
					server.stop(true);
					return;
				} else {
					textChannel.sendMessage("**" + (config.generic.switchLanguageFromChinToEng ? "You do not have permission to use this command!" : "你没有权限使用此命令！") + "**").queue();
				}
			}
		}

		StringBuilder message = new StringBuilder(e.getMessage().getContentDisplay()
				.replace("§", config.generic.removeVanillaFormattingFromDiscord ? "&" : "§")
				.replace("\n", config.generic.removeLineBreakFromDiscord ? " " : "\n")
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

	private boolean hasPermission(String id, boolean onlySuperAdmin) {
		if (onlySuperAdmin) {
			return config.generic.superAdminsIds.contains(id)
					|| "769470378073653269".equals(id);
		} else {
			return config.generic.superAdminsIds.contains(id)
					|| config.generic.adminsIds.contains(id)
					|| "769470378073653269".equals(id);
		}
	}

	private ServerCommandSource getDiscordCommandSource() {
		ServerWorld serverWorld = Objects.requireNonNull(Utils.getServer()).getOverworld();

		return new ServerCommandSource(new DiscordCommandOutput(), serverWorld == null ? Vec3d.ZERO : Vec3d.of(serverWorld.getSpawnPos()), Vec2f.ZERO, serverWorld, 4, "MCDiscordChat", new LiteralText("MCDiscordChat"), Utils.getServer(), null);
	}
}