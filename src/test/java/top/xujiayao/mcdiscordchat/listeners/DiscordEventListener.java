package top.xujiayao.mcdiscordchat.listeners;

import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.util.Objects;

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

			if ("blacklist".equals(command)) {
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
}