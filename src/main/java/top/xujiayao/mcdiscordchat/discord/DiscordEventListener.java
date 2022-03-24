package top.xujiayao.mcdiscordchat.discord;

import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;
import static top.xujiayao.mcdiscordchat.Main.SIMPLE_DATE_FORMAT;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

public class DiscordEventListener extends ListenerAdapter {

	@Override
	public void onMessageReceived(MessageReceivedEvent e) {
//		if (config.generic.bannedDiscord.contains(e.getAuthor().getId())
//				&& !config.generic.adminsIds.contains(e.getAuthor().getId())) return;

		if ((e.getChannel() != CHANNEL)
				|| (e.getMessage().getAuthor() == JDA.getSelfUser())
				|| (e.getAuthor().isBot())) {
			// TODO 如果是自家multiserver的bot该怎么办（可以用id识别）
			if (!e.isWebhookMessage()) {
				return;
			} else {
				// TODO 如果是Webhook该怎么办
				return;
			}
		}

		StringBuilder message = new StringBuilder(EmojiParser.parseToAliases(e.getMessage().getContentDisplay()));

		StringBuilder consoleMessage = new StringBuilder()
				.append("[Discord] <")
				.append(Objects.requireNonNull(e.getMember()).getEffectiveName())
				.append("> ")
				.append(message);

		LOGGER.info(consoleMessage.toString());

		if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
			if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
				MINECRAFT_SEND_COUNT = 0;
				MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
			}

			MINECRAFT_SEND_COUNT++;
			if (MINECRAFT_SEND_COUNT <= 20) {
				CONSOLE_LOG_CHANNEL.sendMessage(TEXTS.consoleLogMessage()
						.replace("%time%", SIMPLE_DATE_FORMAT.format(new Date()))
						.replace("%message%", MarkdownSanitizer.escape(consoleMessage.toString()))).queue();
			}
		}

		// TODO 处理Markdown（message）

		if (!e.getMessage().getAttachments().isEmpty()) {
			if (!e.getMessage().getContentDisplay().isBlank()) {
				message.append(" ");
			}
			for (Message.Attachment attachment : e.getMessage().getAttachments()) {
				message.append(Formatting.YELLOW).append(attachment.isSpoiler() ? "<SPOILER_" : "<");
				if (attachment.isImage()) {
					message.append("image>");
				} else if (attachment.isVideo()) {
					message.append("video>");
				} else {
					message.append("file>");
				}
			}
		}

		if (StringUtils.countMatches(message, ":") >= 2) {
			String[] emoteNames = StringUtils.substringsBetween(message.toString(), ":", ":");
			for (String emoteName : emoteNames) {
				List<Emote> emotes = JDA.getEmotesByName(emoteName, true);
				if (!emotes.isEmpty()) {
					message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.GRAY)));
				} else if (EmojiManager.getForAlias(emoteName) != null) {
					message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.GRAY)));
				}
			}
		}

		if (message.toString().contains("@")) {
			String[] memberNames = StringUtils.substringsBetween(message.toString(), "@", " ");
			if (!StringUtils.substringAfterLast(message.toString(), "@").contains(" ")) {
				memberNames = ArrayUtils.add(memberNames, StringUtils.substringAfterLast(message.toString(), "@"));
			}
			for (String memberName : memberNames) {
				for (Member member : CHANNEL.getMembers()) {
					if (member.getUser().getName().equalsIgnoreCase(memberName)
							|| (member.getNickname() != null && member.getNickname().equalsIgnoreCase(memberName))) {
						message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), ("@" + memberName), (Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.GRAY)));
					}
				}
			}
		}

		@SuppressWarnings("deprecation")
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance instanceof MinecraftServer server) {
			String mcdcText = String.valueOf(Formatting.BLUE) + Formatting.BOLD + "[Discord] " + Formatting.RESET;

			LiteralText roleText = new LiteralText("<" + Objects.requireNonNull(e.getMember()).getEffectiveName() + "> ");
			roleText.setStyle(roleText.getStyle().withColor(TextColor.fromRgb(Objects.requireNonNull(e.getMember()).getColorRaw())));

			StringBuilder finalMessage = message;
			server.getPlayerManager().getPlayerList().forEach(
					player -> player.sendMessage(new LiteralText("")
							.append(mcdcText)
							.append(roleText)
							.append(Formatting.GRAY + finalMessage.toString()), false));
		}
	}
}
