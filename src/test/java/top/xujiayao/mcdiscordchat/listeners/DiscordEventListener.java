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
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.util.Objects;

import static top.xujiayao.mcdiscordchat.Main.config;

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