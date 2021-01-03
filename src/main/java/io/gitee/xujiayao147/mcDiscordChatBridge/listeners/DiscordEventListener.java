package io.gitee.xujiayao147.mcDiscordChatBridge.listeners;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.DiscordCommandOutput;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.MarkdownParser;
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
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		MinecraftServer server = getServer();
		if (e.getAuthor() != e.getJDA().getSelfUser() && !e.getAuthor().isBot()
				&& e.getChannel().getId().equals(Main.config.channelId) && server != null) {
			if (Main.config.bannedDiscord.contains(e.getAuthor().getId()) && !Arrays.asList(Main.config.adminsIds).contains(e.getAuthor().getId())) {
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
			} else if (e.getMessage().getContentRaw().startsWith("!help")) {
				String help = "```\n" + "=============== 命令 ===============\n" + "\n" + "!online: 列出服务器在线玩家\n"
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

	public ServerCommandSource getDiscordCommandSource() {
		ServerWorld serverWorld = Objects.requireNonNull(getServer()).getOverworld();
		return new ServerCommandSource(new DiscordCommandOutput(),
				serverWorld == null ? Vec3d.ZERO : Vec3d.of(serverWorld.getSpawnPos()), Vec2f.ZERO, serverWorld, 4,
				"Discord", new LiteralText("Discord"), getServer(), null);
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