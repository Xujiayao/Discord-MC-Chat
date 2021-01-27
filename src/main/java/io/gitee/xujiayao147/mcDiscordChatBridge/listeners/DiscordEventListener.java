package io.gitee.xujiayao147.mcDiscordChatBridge.listeners;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import io.gitee.xujiayao147.mcDiscordChatBridge.objects.Player;
import io.gitee.xujiayao147.mcDiscordChatBridge.objects.Stats;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.MarkdownParser;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.Utils;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		MinecraftServer server = getServer();
		if (e.getAuthor() != e.getJDA().getSelfUser() && !e.getAuthor().isBot()
			  && e.getChannel().getId().equals(Main.config.channelId) && server != null) {
			if (Main.config.bannedDiscord.contains(e.getAuthor().getId())
				  && !Arrays.asList(Main.config.adminsIds).contains(e.getAuthor().getId())) {
				return;
			}

			if (e.getMessage().getContentRaw().startsWith("!online")) {
				List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
				if (onlinePlayers.size() == 0) {
					e.getChannel().sendMessage(
						  "```\n=============== 在线玩家 (" + onlinePlayers.size() + ") ===============\n\n当前没有在线玩家！```")
						  .queue();
				} else {
					StringBuilder playerList = new StringBuilder(
						  "```\n=============== 在线玩家 (" + onlinePlayers.size() + ") ===============\n");
					for (ServerPlayerEntity player : onlinePlayers) {
						playerList.append("\n").append(player.getEntityName());
					}

					playerList.append("```");
					e.getChannel().sendMessage(playerList.toString()).queue();
				}
			} else if (e.getMessage().getContentRaw().startsWith("!scoreboard")) {
				BufferedReader reader = null;
				FileReader fileReader = null;

				try {
					String temp = e.getMessage().getContentRaw().replace("!scoreboard ", "");

					String type = temp.substring(0, temp.lastIndexOf(" ") - 1);
					String id = temp.substring(temp.indexOf(" ") + 1);

					reader = new BufferedReader(new FileReader(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString() + "/usercache.json"));

					String jsonString = reader.readLine();

					Gson gson = new Gson();
					Type userListType = new TypeToken<ArrayList<Player>>() {
					}.getType();

					Main.config.playerList = gson.fromJson(jsonString, userListType);

					Main.config.statsFileList = Utils.getFileList(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString() + "/" + Main.config.worldName + "/stats/"));
					Main.config.statsList = new ArrayList<>();

					for (File file : Main.config.statsFileList) {
						fileReader = new FileReader(file);
						reader = new BufferedReader(fileReader);

						for (Player player : Main.config.playerList)
							if (player.getUuid().equals(file.getName().replace(".json", "")))
								Main.config.statsList.add(new Stats(player.getName(), reader.readLine()));
					}

					Main.config.scoreboardMap = new HashMap<>();

					for (Stats stats : Main.config.statsList) {
						temp = stats.getContent();

						if (!temp.contains("minecraft:" + type))
							continue;

						temp = temp.substring(temp.indexOf("minecraft:" + type));
						temp = temp.substring(0, temp.indexOf("}"));

						if (!temp.contains("minecraft:" + id))
							continue;

						temp = temp.substring(temp.indexOf("minecraft:" + id) + ("minecraft:" + id).length() + 2);

						if (temp.contains(","))
							temp = temp.substring(0, temp.indexOf(","));

						Main.config.scoreboardMap.put(stats.getName(), Integer.valueOf(temp));
					}

					List<Map.Entry<String, Integer>> entryList = new ArrayList<>(Main.config.scoreboardMap.entrySet());

					entryList.sort((o1, o2) -> (o2.getValue() - o1.getValue()));

					StringBuilder output = new StringBuilder("```\n=============== 排行榜 ===============\n");

					for (Map.Entry<String, Integer> entry : entryList) {
						output.append(String.format("\n%-8d %-8s", entry.getValue(), entry.getKey()));
					}

					output.append("```");
					e.getChannel().sendMessage(output).queue();

					reader.close();
					if (fileReader != null)
						fileReader.close();
				} catch (Exception e1) {
					e1.printStackTrace();
				} finally {
					try {
						if (reader != null)
							reader.close();
						if (fileReader != null)
							fileReader.close();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			} else if (e.getMessage().getContentRaw().startsWith("!help")) {
				String help = "```\n" + "=============== 命令 ===============\n"
					  + "\n"
					  + "!online: 列出服务器在线玩家\n"
					  + "!scoreboard <type> <id>: 查询该统计信息的玩家排行榜\n"
					  + "!ban <type> <id/name>: 将一名 Discord 用户或 Minecraft 玩家从黑名单中添加或移除（仅限管理员）\n"
					  + "!banlist: 列出黑名单```";
				e.getChannel().sendMessage(help).queue();

			} else if (e.getMessage().getContentRaw().startsWith("!banlist")) {
				StringBuilder bannedList = new StringBuilder("```\n=============== 黑名单 ===============\n\nDiscord:");
				if (Main.config.bannedDiscord.size() == 0) {
					bannedList.append("\n列表为空！");
				}
				for (String id : Main.config.bannedDiscord) {
					bannedList.append("\n").append(id);
				}
				bannedList.append("\n\nMinecraft:");
				if (Main.config.bannedMinecraft.size() == 0) {
					bannedList.append("\n列表为空！");
				}
				for (String name : Main.config.bannedMinecraft) {
					bannedList.append("\n").append(name);
				}
				bannedList.append("```");
				e.getChannel().sendMessage(bannedList.toString()).queue();
			} else if (e.getMessage().getContentRaw().startsWith("!ban")) {
				if (!Arrays.asList(Main.config.adminsIds).contains(e.getAuthor().getId())) {
					e.getChannel().sendMessage("**你没有权限使用此命令！**").queue();
					return;
				}

				try {
					String command = e.getMessage().getContentRaw().replace("!ban ", "");

					if (command.startsWith("discord")) {
						command = command.replace("discord ", "");

						if (Main.config.bannedDiscord.contains(command)) {
							Main.config.bannedDiscord.remove(command);
							e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 移出黑名单！**").queue();
						} else {
							Main.config.bannedDiscord.add(command);
							e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 添加至黑名单！**").queue();
						}
					} else if (command.startsWith("minecraft")) {
						command = command.replace("minecraft ", "");

						if (Main.config.bannedMinecraft.contains(command)) {
							Main.config.bannedMinecraft.remove(command);
							e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 移出黑名单！**").queue();
						} else {
							Main.config.bannedMinecraft.add(command);
							e.getChannel().sendMessage("**已将 " + command.replace("_", "\\_") + " 添加至黑名单！**").queue();
						}
					}
				} catch (Exception e2) {
					e.getChannel().sendMessage("**命令错误！**").queue();
				}
			}

			LiteralText msg = new LiteralText(Main.config.texts.messageText
				  .replace("%discordname%", Objects.requireNonNull(e.getMember()).getEffectiveName())
				  .replace("%message%",
					    MarkdownParser.parseMarkdown(e.getMessage().getContentDisplay()
							+ ((e.getMessage().getAttachments().size() > 0) ? " <att>" : "")
							+ ((e.getMessage().getEmbeds().size() > 0) ? " <embed>" : ""))));
			msg.setStyle(msg.getStyle().withColor(TextColor.fromFormatting(Formatting.GRAY)));
			server.getPlayerManager().getPlayerList().forEach(
				  serverPlayerEntity -> serverPlayerEntity.sendMessage(new LiteralText("").append(msg), false));
		}

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