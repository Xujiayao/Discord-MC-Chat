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
			if (e.getMessage().getContentRaw().startsWith("!command")
					&& Arrays.asList(Main.config.adminsIds).contains(e.getAuthor().getId())) {
				String command = e.getMessage().getContentRaw().replace("!command ", "");
				server.getCommandManager().execute(getDiscordCommandSource(), command);
			} else if (e.getMessage().getContentRaw().startsWith("!online")) {
				List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
				if (onlinePlayers.size() == 0) {
					e.getChannel().sendMessage("```\n=============== 在线玩家 (" + onlinePlayers.size()
							+ ") ===============\n\n**当前没有在线玩家！**```").queue();
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
				String help = "```\n" + "=============== 命令 ===============\n" + "\n" + "!online: 列出服务器在线玩家" + "\n"
						+ "!command <command>: 在服务器命令行中执行命令（仅限管理员）\n```";
				e.getChannel().sendMessage(help).queue();

			} else {
				LiteralText discord = new LiteralText(Main.config.texts.coloredText
						.replace("%discordname%", Objects.requireNonNull(e.getMember()).getEffectiveName())
						.replace("%message%",
								e.getMessage().getContentDisplay()
										+ ((e.getMessage().getAttachments().size() > 0) ? " <att>" : "")
										+ ((e.getMessage().getEmbeds().size() > 0) ? " <embed>" : "")));
				discord.setStyle(discord.getStyle()
						.withColor(TextColor.fromRgb(Objects.requireNonNull(e.getMember()).getColorRaw())));
				LiteralText msg = new LiteralText(Main.config.texts.colorlessText
						.replace("%discordname%", Objects.requireNonNull(e.getMember()).getEffectiveName())
						.replace("%message%",
								MarkdownParser.parseMarkdown(e.getMessage().getContentDisplay()
										+ ((e.getMessage().getAttachments().size() > 0) ? " <att>" : "")
										+ ((e.getMessage().getEmbeds().size() > 0) ? " <embed>" : ""))));
				msg.setStyle(msg.getStyle().withColor(TextColor.fromFormatting(Formatting.WHITE)));
				server.getPlayerManager().getPlayerList().forEach(serverPlayerEntity -> serverPlayerEntity
						.sendMessage(new LiteralText("").append(discord).append(msg), false));
			}
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