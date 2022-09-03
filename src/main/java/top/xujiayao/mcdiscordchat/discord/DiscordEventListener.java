package top.xujiayao.mcdiscordchat.discord;

import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.fabricmc.loader.api.FabricLoader;
//#if MC <= 11605
//$$ import net.minecraft.server.command.ServerCommandSource;
//#endif
//#if MC <= 11802
//$$ import net.minecraft.text.LiteralText;
//#endif
import net.minecraft.text.Text;
//#if MC >= 11900
import net.minecraft.text.Texts;
//#endif
import net.minecraft.util.Formatting;
//#if MC <= 11605
//$$ import net.minecraft.util.math.Vec2f;
//$$ import net.minecraft.util.math.Vec3d;
//#endif
//#if MC <= 11502
//$$ import net.minecraft.world.dimension.DimensionType;
//#endif
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Translations;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;

/**
 * @author Xujiayao
 */
public class DiscordEventListener extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
		e.deferReply().queue();

		if (SERVER == null) {
			e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You need to wait for the server to fully start!**" : "**你需要等待服务器完全启动！**").queue();
			return;
		}

		if (!e.isFromGuild()) {
			e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You cannot use this command via direct message!**" : "**你不能通过私信使用此命令！**").queue();
			return;
		}

		LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
				.replace("%server%", (CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"))
				.replace("%message%", Translations.translateMessage("message.unformattedCommandNotice")
						.replace("%name%", CONFIG.generic.useServerNickname ? Objects.requireNonNull(e.getMember()).getEffectiveName() : Objects.requireNonNull(e.getMember()).getUser().getName())
						.replace("%roleName%", e.getMember().getRoles().get(0).getName())
						.replace("%command%", e.getCommandString())));

		Text commandNoticeText = Text.Serializer.fromJson(Translations.translateMessage("message.formattedCommandNotice")
				.replace("%server%", (CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"))
				.replace("%name%", (CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()).replace("\\", "\\\\"))
				.replace("%roleName%", e.getMember().getRoles().get(0).getName())
				.replace("%roleColor%", "#" + Integer.toHexString(e.getMember().getColorRaw()))
				.replace("%command%", e.getCommandString()));

		//#if MC <= 11802
		//$$ SERVER.getPlayerManager().getPlayerList().forEach(
		//$$ 		player -> player.sendMessage(new LiteralText("")
		//$$ 				.append(Text.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
		//$$ 						.replace("%server%", (CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"))
		//$$ 						.replace("%message%", "")))
		//$$ 				.append(commandNoticeText), false));
		//#else
		List<Text> commandNoticeTextList = new ArrayList<>();
		commandNoticeTextList.add(Text.Serializer.fromJson(Translations.translateMessage("message.formattedOtherMessage")
				.replace("%server%", (CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"))
				.replace("%message%", "")));
		commandNoticeTextList.add(commandNoticeText);

		SERVER.getPlayerManager().getPlayerList().forEach(
				player -> player.sendMessage(Texts.join(commandNoticeTextList, Text.of("")), false));
		//#endif

		if (CONFIG.multiServer.enable) {
			MULTI_SERVER.sendMessage(false, false, true, null, Text.Serializer.toJson(commandNoticeText));
		}

		switch (e.getName()) {
			case "info" -> {
				e.getHook().sendMessage("```\n" + Utils.getInfoCommandMessage() + "\n```").queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(true, false, false, null, "{\"type\":\"discordInfoCommand\",\"channel\":\"" + e.getChannel().getId() + "\"}");
				}
			}
			case "help" -> e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? """
					```
					=============== Help ===============
					/info                | Query server running status
					/help                | Get a list of available commands
					/update              | Check for update
					/stats <type> <name> | Query the scoreboard of a statistic
					/reload              | Reload MCDiscordChat config file (admin only)
					/console <command>   | Execute a command in the server console (admin only)
					/log                 | Get the specified server log (admin only)
					/stop                | Stop the server (admin only)
					```""" : """
					```
					=============== 帮助 ===============
					/info                | 查询服务器运行状态
					/help                | 获取可用命令列表
					/update              | 检查更新
					/stats <type> <name> | 查询该统计信息的排行榜
					/reload              | 重新加载 MCDiscordChat 配置文件（仅限管理员）
					/console <command>   | 在服务器控制台中执行命令（仅限管理员）
					/log                 | 获取指定的服务器日志（仅限管理员）
					/stop                | 停止服务器（仅限管理员）
					```""").queue();
			case "update" -> e.getHook().sendMessage(Utils.checkUpdate(true)).queue();
			case "stats" -> {
				String type = Objects.requireNonNull(e.getOption("type")).getAsString();
				String name = Objects.requireNonNull(e.getOption("name")).getAsString();
				e.getHook().sendMessage("```\n" + Utils.getStatsCommandMessage(type, name) + "\n```").queue();
			}
			case "reload" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					e.getHook().sendMessage(Utils.reload()).queue();
				} else {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You do not have permission to use this command!**" : "**你没有权限使用此命令！**").queue();
				}
			}
			case "console" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					String command = Objects.requireNonNull(e.getOption("command")).getAsString();
					if (command.equals("stop") || command.equals("/stop")) {
						e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Stopping the server!**" : "**正在停止服务器！**")
								.submit()
								.whenComplete((v, ex) -> SERVER.stop(true));
					} else {
						e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Executing the command!**" : "**正在执行命令！**")
								.submit()
								.whenComplete((v, ex) -> SERVER.getCommandManager()
										//#if MC >= 11900
										.executeWithPrefix(SERVER.getCommandSource().withOutput(new DiscordCommandOutput(e)), command));
										//#elseif MC >= 11700
										//$$ .execute(SERVER.getCommandSource().withOutput(new DiscordCommandOutput(e)), command));
										//#elseif MC >= 11600
										//$$ .execute(new ServerCommandSource(new DiscordCommandOutput(e), Vec3d.ZERO, Vec2f.ZERO, SERVER.getOverworld(), 4, "MCDiscordChat", new LiteralText("MCDiscordChat"), SERVER, null), command));
										//#else
										//$$ .execute(new ServerCommandSource(new DiscordCommandOutput(e), Vec3d.ZERO, Vec2f.ZERO, SERVER.getWorld(DimensionType.OVERWORLD), 4, "MCDiscordChat", new LiteralText("MCDiscordChat"), SERVER, null), command));
										//#endif
					}
				} else {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You do not have permission to use this command!**" : "**你没有权限使用此命令！**").queue();
				}
			}
			case "log" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					String fileName = Objects.requireNonNull(e.getOption("file")).getAsString();

					if (fileName.equals("latest.log")) {
						e.getHook().sendFiles(FileUpload.fromData(new File(FabricLoader.getInstance().getGameDir().toFile(), "logs/latest.log"))).queue();
					} else {
						File source = new File(FabricLoader.getInstance().getGameDir().toFile(), ("logs/" + fileName));
						try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(source))) {
							byte[] buffer = new byte[1024];
							ByteArrayOutputStream out = new ByteArrayOutputStream();

							int len;
							while ((len = gis.read(buffer)) > 0) {
								out.write(buffer, 0, len);
							}

							e.getHook().sendFiles(FileUpload.fromData(out.toByteArray(), "target.log")).queue();
						} catch (FileNotFoundException ex) {
							e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Could not find the specified file!**" : "**找不到指定的文件！**").queue();
						} catch (Exception ex) {
							LOGGER.error(ExceptionUtils.getStackTrace(ex));
						}
					}
				} else {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You do not have permission to use this command!**" : "**你没有权限使用此命令！**").queue();
				}
			}
			case "stop" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Stopping the server!**" : "**正在停止服务器！**")
							.submit()
							.whenComplete((v, ex) -> SERVER.stop(true));
				} else {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You do not have permission to use this command!**" : "**你没有权限使用此命令！**").queue();
				}
			}
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
		if (e.getName().equals("log") && e.getFocusedOption().getName().equals("file")) {
			String[] files = new File(FabricLoader.getInstance().getGameDir().toFile(), "logs").list();
			files = Arrays.stream(Objects.requireNonNull(files))
					.filter(file -> file.contains(e.getFocusedOption().getValue()))
					.toArray(String[]::new);

			if (files.length > 25) {
				files = Arrays.copyOfRange(files, 0, 25);
				files[24] = "...";
			}

			List<Command.Choice> options = Stream.of(files)
					.map(file -> new Command.Choice(file, file))
					.collect(Collectors.toList());

			e.replyChoices(options).queue();
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent e) {
		if ((e.getChannel() != CHANNEL)
				|| (e.getAuthor() == JDA.getSelfUser())
				|| (e.isWebhookMessage())) {
			return;
		}

		if (CONFIG.multiServer.botIds.contains(e.getAuthor().getId())) {
			return;
		}

		Member referencedMember = null;
		String memberRoleName;
		String referencedMemberRoleName = "null";
		String webhookName = "null";

		if (e.getMessage().getReferencedMessage() != null) {
			try {
				referencedMember = Objects.requireNonNull(e.getMessage().getReferencedMessage().getMember());
			} catch (Exception ex) {
				webhookName = e.getMessage().getReferencedMessage().getAuthor().getName();
			}

			try {
				referencedMemberRoleName = Objects.requireNonNull(referencedMember).getRoles().get(0).getName();
			} catch (Exception ex) {
				referencedMemberRoleName = "null";
			}

			LOGGER.info(Translations.translateMessage("message.unformattedResponseMessage")
					.replace("%server%", "Discord")
					.replace("%name%", (referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()) : webhookName)
					.replace("%roleName%", referencedMemberRoleName)
					.replace("%message%", EmojiParser.parseToAliases(e.getMessage().getReferencedMessage().getContentDisplay())));
		}

		try {
			memberRoleName = Objects.requireNonNull(e.getMember()).getRoles().get(0).getName();
		} catch (Exception ex) {
			memberRoleName = "null";
		}

		LOGGER.info(Translations.translateMessage("message.unformattedChatMessage")
				.replace("%server%", "Discord")
				.replace("%name%", CONFIG.generic.useServerNickname ? Objects.requireNonNull(e.getMember()).getEffectiveName() : Objects.requireNonNull(e.getMember()).getUser().getName())
				.replace("%roleName%", memberRoleName)
				.replace("%message%", EmojiParser.parseToAliases(e.getMessage().getContentDisplay())));

		if (SERVER == null) {
			return;
		}

		String textBeforePlaceholder = "";
		String textAfterPlaceholder = "";

		String[] arrayParts = StringUtils.substringsBetween(Translations.translateMessage("message.formattedChatMessage"), "{", "}");
		for (String arrayPart : arrayParts) {
			if (arrayPart.contains("%message%")) {
				textBeforePlaceholder = StringUtils.substringBefore(arrayPart, "%message%");
				textAfterPlaceholder = StringUtils.substringAfter(arrayPart, "%message%");
			}
		}

		StringBuilder referencedMessage;
		String finalReferencedMessage = "";

		if (e.getMessage().getReferencedMessage() != null) {
			referencedMessage = new StringBuilder(EmojiParser.parseToAliases(e.getMessage().getReferencedMessage().getContentDisplay()));

			if (CONFIG.generic.formatChatMessages && !e.getMessage().getReferencedMessage().getAttachments().isEmpty()) {
				if (!e.getMessage().getReferencedMessage().getContentDisplay().isBlank()) {
					referencedMessage.append(" ");
				}
				for (Message.Attachment attachment : e.getMessage().getReferencedMessage().getAttachments()) {
					referencedMessage.append(Formatting.YELLOW).append(attachment.isSpoiler() ? "<SPOILER_" : "<");
					if (attachment.isImage()) {
						referencedMessage.append("image>");
					} else if (attachment.isVideo()) {
						referencedMessage.append("video>");
					} else {
						referencedMessage.append("file>");
					}
				}
			}

			if (CONFIG.generic.formatChatMessages && !e.getMessage().getReferencedMessage().getStickers().isEmpty()) {
				if (!e.getMessage().getReferencedMessage().getContentDisplay().isBlank()) {
					referencedMessage.append(" ");
				}
				for (StickerItem ignored : e.getMessage().getReferencedMessage().getStickers()) {
					referencedMessage.append(Formatting.YELLOW).append("<sticker>");
				}
			}

			if (CONFIG.generic.formatChatMessages && StringUtils.countMatches(referencedMessage, ":") >= 2) {
				String[] emojiNames = StringUtils.substringsBetween(referencedMessage.toString(), ":", ":");
				for (String emojiName : emojiNames) {
					List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
					if (!emojis.isEmpty()) {
						referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.DARK_GRAY)));
					} else if (EmojiManager.getForAlias(emojiName) != null) {
						referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.DARK_GRAY)));
					}
				}
			}

			if (CONFIG.generic.formatChatMessages && referencedMessage.toString().contains("@")) {
				String[] memberNames = StringUtils.substringsBetween(referencedMessage.toString(), "@", " ");
				if (!StringUtils.substringAfterLast(referencedMessage.toString(), "@").contains(" ")) {
					memberNames = ArrayUtils.add(memberNames, StringUtils.substringAfterLast(referencedMessage.toString(), "@"));
				}
				for (String memberName : memberNames) {
					for (Member member : CHANNEL.getMembers()) {
						if (member.getUser().getName().equalsIgnoreCase(memberName)
								|| (member.getNickname() != null && member.getNickname().equalsIgnoreCase(memberName))) {
							referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), ("@" + memberName), (Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.DARK_GRAY)));
						}
					}
				}
			}

			finalReferencedMessage = MarkdownParser.parseMarkdown(referencedMessage.toString());

			for (String protocol : new String[]{"http://", "https://"}) {
				if (CONFIG.generic.formatChatMessages && finalReferencedMessage.contains(protocol)) {
					String[] links = StringUtils.substringsBetween(finalReferencedMessage, protocol, " ");
					if (!StringUtils.substringAfterLast(finalReferencedMessage, protocol).contains(" ")) {
						links = ArrayUtils.add(links, StringUtils.substringAfterLast(finalReferencedMessage, protocol));
					}
					for (String link : links) {
						if (link.contains("\n")) {
							link = StringUtils.substringBefore(link, "\n");
						}

						String hyperlinkInsert;
						if (StringUtils.containsIgnoreCase(link, "gif")
								&& StringUtils.containsIgnoreCase(link, "tenor.com")) {
							hyperlinkInsert = textAfterPlaceholder + "},{\"text\":\"<gif>\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder;
						} else {
							hyperlinkInsert = textAfterPlaceholder + "},{\"text\":\"" + protocol + link + "\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder;
						}
						finalReferencedMessage = StringUtils.replaceIgnoreCase(finalReferencedMessage, (protocol + link), hyperlinkInsert);
					}
				}
			}
		}

		StringBuilder message = new StringBuilder(EmojiParser.parseToAliases(e.getMessage().getContentDisplay()));

		if (CONFIG.generic.formatChatMessages && !e.getMessage().getAttachments().isEmpty()) {
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

		if (CONFIG.generic.formatChatMessages && !e.getMessage().getStickers().isEmpty()) {
			if (!e.getMessage().getContentDisplay().isBlank()) {
				message.append(" ");
			}
			for (StickerItem ignored : e.getMessage().getStickers()) {
				message.append(Formatting.YELLOW).append("<sticker>");
			}
		}

		if (CONFIG.generic.formatChatMessages && StringUtils.countMatches(message, ":") >= 2) {
			String[] emojiNames = StringUtils.substringsBetween(message.toString(), ":", ":");
			for (String emojiName : emojiNames) {
				List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
				if (!emojis.isEmpty()) {
					message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.GRAY)));
				} else if (EmojiManager.getForAlias(emojiName) != null) {
					message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.GRAY)));
				}
			}
		}

		if (CONFIG.generic.formatChatMessages && message.toString().contains("@")) {
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

		String finalMessage = MarkdownParser.parseMarkdown(message.toString());

		for (String protocol : new String[]{"http://", "https://"}) {
			if (CONFIG.generic.formatChatMessages && finalMessage.contains(protocol)) {
				String[] links = StringUtils.substringsBetween(finalMessage, protocol, " ");
				if (!StringUtils.substringAfterLast(finalMessage, protocol).contains(" ")) {
					links = ArrayUtils.add(links, StringUtils.substringAfterLast(finalMessage, protocol));
				}
				for (String link : links) {
					if (link.contains("\n")) {
						link = StringUtils.substringBefore(link, "\n");
					}

					String hyperlinkInsert;
					if (StringUtils.containsIgnoreCase(link, "gif")
							&& StringUtils.containsIgnoreCase(link, "tenor.com")) {
						hyperlinkInsert = textAfterPlaceholder + "},{\"text\":\"<gif>\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder;
					} else {
						hyperlinkInsert = textAfterPlaceholder + "},{\"text\":\"" + protocol + link + "\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder;
					}
					finalMessage = StringUtils.replaceIgnoreCase(finalMessage, (protocol + link), hyperlinkInsert);
				}
			}
		}

		if (e.getMessage().getReferencedMessage() != null) {
			Text referenceFinalText = Text.Serializer.fromJson(Translations.translateMessage("message.formattedResponseMessage")
					.replace("%server%", "Discord")
					.replace("%name%", (referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()).replace("\\", "\\\\") : webhookName)
					.replace("%roleName%", referencedMemberRoleName)
					.replace("%roleColor%", "#" + Integer.toHexString((referencedMember != null) ? referencedMember.getColorRaw() : Role.DEFAULT_COLOR_RAW))
					.replace("%message%", finalReferencedMessage));

			SERVER.getPlayerManager().getPlayerList().forEach(
					player -> player.sendMessage(referenceFinalText, false));
		}

		Text finalText = Text.Serializer.fromJson(Translations.translateMessage("message.formattedChatMessage")
				.replace("%server%", "Discord")
				.replace("%name%", (CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()).replace("\\", "\\\\"))
				.replace("%roleName%", memberRoleName)
				.replace("%roleColor%", "#" + Integer.toHexString(Objects.requireNonNull(e.getMember()).getColorRaw()))
				.replace("%message%", finalMessage));

		SERVER.getPlayerManager().getPlayerList().forEach(
				player -> player.sendMessage(finalText, false));
	}
}
