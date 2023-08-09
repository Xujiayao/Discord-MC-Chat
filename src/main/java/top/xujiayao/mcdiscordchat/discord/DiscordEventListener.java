package top.xujiayao.mcdiscordchat.discord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
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
import java.util.Map;
import java.util.Objects;
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
			e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.serverNotStarted")).queue();
			return;
		}

		if (!e.isFromGuild()) {
			e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.notFromGuild")).queue();
			return;
		}

		String roleName;
		try {
			roleName = Objects.requireNonNull(e.getMember()).getRoles().get(0).getName();
		} catch (Exception ex) {
			roleName = "null";
		}

		LOGGER.info(Translations.translateMessage("message.unformattedOtherMessage")
				.replace("%server%", (CONFIG.multiServer.enable ? CONFIG.multiServer.name : "Discord"))
				.replace("%message%", Translations.translateMessage("message.unformattedCommandNotice")
						.replace("%name%", CONFIG.generic.useServerNickname ? Objects.requireNonNull(e.getMember()).getEffectiveName() : Objects.requireNonNull(e.getMember()).getUser().getName())
						.replace("%roleName%", roleName)
						.replace("%command%", e.getCommandString())));

		if (CONFIG.generic.broadcastSlashCommandExecution) {
			Text commandNoticeText = Text.Serializer.fromJson(Translations.translateMessage("message.formattedCommandNotice")
					.replace("%name%", (CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()).replace("\\", "\\\\").replace("\"", "\\\""))
					.replace("%roleName%", roleName)
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
		}

		switch (e.getName()) {
			case "help" -> e.getHook().sendMessage("```\n" + Utils.getHelpCommandMessage(true) + "\n```").queue();
			case "info" -> {
				e.getHook().sendMessage("```\n" + Utils.getInfoCommandMessage() + "\n```").queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(true, false, false, null, "{\"type\":\"discordInfoCommand\",\"channel\":\"" + e.getChannel().getId() + "\"}");
				}
			}
			case "stats" -> {
				String type = Objects.requireNonNull(e.getOption("type")).getAsString();
				String name = Objects.requireNonNull(e.getOption("name")).getAsString();
				e.getHook().sendMessage("```\n" + Utils.getStatsCommandMessage(type, name) + "\n```").queue();
			}
			case "update" -> e.getHook().sendMessage(Utils.checkUpdate(true)).queue();
			case "whitelist" -> {
				String player = Objects.requireNonNull(e.getOption("player")).getAsString();
				e.getHook().sendMessage(Utils.whitelist(player)).queue();
			}
			case "console" -> {
				if (Utils.isAdmin(e.getMember())) {
					String command = Objects.requireNonNull(e.getOption("command")).getAsString();
					if ("stop".equals(command) || "/stop".equals(command)) {
						e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.stoppingServer"))
								.submit()
								.whenComplete((v, ex) -> SERVER.stop(true));
					} else {
						e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.executingCommand"))
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
					e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.noPermission")).queue();
				}
			}
			case "log" -> {
				if (Utils.isAdmin(e.getMember())) {
					String fileName = Objects.requireNonNull(e.getOption("file")).getAsString();

					if ("latest.log".equals(fileName)) {
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
							e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.fileNotFound")).queue();
						} catch (Exception ex) {
							LOGGER.error(ExceptionUtils.getStackTrace(ex));
						}
					}
				} else {
					e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.noPermission")).queue();
				}
			}
			case "reload" -> {
				if (Utils.isAdmin(e.getMember())) {
					e.getHook().sendMessage(Utils.reload()).queue();
				} else {
					e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.noPermission")).queue();
				}
			}
			case "stop" -> {
				if (Utils.isAdmin(e.getMember())) {
					e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.stoppingServer"))
							.submit()
							.whenComplete((v, ex) -> SERVER.stop(true));
				} else {
					e.getHook().sendMessage(Translations.translate("discord.deListener.oscInteraction.noPermission")).queue();
				}
			}
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
		if ("log".equals(e.getName()) && "file".equals(e.getFocusedOption().getName())) {
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
					.toList();

			e.replyChoices(options).queue();
		} else if ("console".equals(e.getName()) && "command".equals(e.getFocusedOption().getName())) {
			CommandDispatcher<ServerCommandSource> dispatcher = SERVER.getCommandManager().getDispatcher();

			try {
				String input = e.getFocusedOption().getValue();
				if (input.startsWith("/")) {
					input = input.substring(1);
				}

				List<String> temp = new ArrayList<>();

				ParseResults<ServerCommandSource> results = dispatcher.parse(input, SERVER.getCommandSource());
				Suggestions suggestions = dispatcher.getCompletionSuggestions(results).get();

				int size = results.getContext().getNodes().size();
				if (size > 0) {
					Map<CommandNode<ServerCommandSource>, String> map = dispatcher.getSmartUsage(results.getContext().getNodes().get(size - 1).getNode(), SERVER.getCommandSource());

					for (String string : map.values()) {
						temp.add((string.length() > 100) ? string.substring(0, 99) : string);
					}
				}

				for (Suggestion suggestion : suggestions.getList()) {
					temp.add(suggestion.apply(input));
				}

				String[] commands = temp.toArray(new String[0]);

				if (commands.length > 25) {
					commands = Arrays.copyOfRange(commands, 0, 25);
					commands[24] = "...";
				}

				List<Command.Choice> options = Stream.of(commands)
						.map(command -> new Command.Choice(command, command))
						.toList();

				e.replyChoices(options).queue();
			} catch (Exception ex) {
				LOGGER.error(ExceptionUtils.getStackTrace(ex));
			}
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

		Objects.requireNonNull(e.getMember());

		Member referencedMember = null;
		String memberRoleName;
		String referencedMemberRoleName = "null";
		String webhookName = "null";

		String referencedMessageTemp = "null";
		String messageTemp = e.getMessage().getContentDisplay();

		if (e.getMessage().getReferencedMessage() != null) {
			referencedMessageTemp = e.getMessage().getReferencedMessage().getContentDisplay();

			if (StringUtils.countMatches(referencedMessageTemp, "\n") > CONFIG.generic.discordNewlineLimit) {
				referencedMessageTemp = referencedMessageTemp.substring(0, StringUtils.ordinalIndexOf(referencedMessageTemp, "\n", CONFIG.generic.discordNewlineLimit) + 1) + "...";
			}

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
					.replace("%message%", EmojiParser.parseToAliases(referencedMessageTemp)));
		}

		if (StringUtils.countMatches(messageTemp, "\n") > CONFIG.generic.discordNewlineLimit) {
			messageTemp = messageTemp.substring(0, StringUtils.ordinalIndexOf(messageTemp, "\n", CONFIG.generic.discordNewlineLimit) + 1) + "...";
		}

		try {
			memberRoleName = e.getMember().getRoles().get(0).getName();
		} catch (Exception ex) {
			memberRoleName = "null";
		}

		LOGGER.info(Translations.translateMessage("message.unformattedChatMessage")
				.replace("%server%", "Discord")
				.replace("%name%", CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName())
				.replace("%roleName%", memberRoleName)
				.replace("%message%", EmojiParser.parseToAliases(messageTemp)));

		if (SERVER == null) {
			return;
		}

		String[] textBeforePlaceholder = new String[2];
		String[] textAfterPlaceholder = new String[2];
		for (String part : StringUtils.substringsBetween(Translations.translateMessage("message.formattedResponseMessage"), "{", "}")) {
			if (part.contains("%message%")) {
				textBeforePlaceholder[0] = StringUtils.substringBefore(part, "%message%");
				textAfterPlaceholder[0] = StringUtils.substringAfter(part, "%message%");
			}
		}
		for (String part : StringUtils.substringsBetween(Translations.translateMessage("message.formattedChatMessage"), "{", "}")) {
			if (part.contains("%message%")) {
				textBeforePlaceholder[1] = StringUtils.substringBefore(part, "%message%");
				textAfterPlaceholder[1] = StringUtils.substringAfter(part, "%message%");
			}
		}

		String finalReferencedMessage = "";
		String finalMessage = "";

		if (CONFIG.generic.formatChatMessages) {
			StringBuilder referencedMessage;

			if (e.getMessage().getReferencedMessage() != null) {
				referencedMessage = new StringBuilder(EmojiParser.parseToAliases(referencedMessageTemp));

				if (!e.getMessage().getReferencedMessage().getAttachments().isEmpty()) {
					if (!referencedMessageTemp.isBlank()) {
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

				if (!e.getMessage().getReferencedMessage().getStickers().isEmpty()) {
					if (!referencedMessageTemp.isBlank()) {
						referencedMessage.append(" ");
					}
					for (int i = 0; i < e.getMessage().getReferencedMessage().getStickers().size(); i++) {
						referencedMessage.append(Formatting.YELLOW).append("<sticker>");
					}
				}

				if (StringUtils.countMatches(referencedMessage, ":") >= 2) {
					String[] emojiNames = StringUtils.substringsBetween(referencedMessage.toString(), ":", ":");
					for (String emojiName : emojiNames) {
						List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
						if (!emojis.isEmpty() || EmojiManager.getForAlias(emojiName) != null) {
							referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.RESET)));
						}
					}
				}

				if (referencedMessage.toString().contains("@")) {
					String temp = referencedMessage.toString();

					for (Member member : CHANNEL.getMembers()) {
						String usernameMention = "@" + member.getUser().getName();
						String displayNameMention = "@" + member.getUser().getEffectiveName();
						String formattedMention = Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.RESET;
						temp = StringUtils.replaceIgnoreCase(temp, usernameMention, MarkdownSanitizer.escape(formattedMention));
						temp = StringUtils.replaceIgnoreCase(temp, displayNameMention, MarkdownSanitizer.escape(formattedMention));

						if (member.getNickname() != null) {
							String nicknameMention = "@" + member.getNickname();
							temp = StringUtils.replaceIgnoreCase(temp, nicknameMention, MarkdownSanitizer.escape(formattedMention));
						}
					}
					for (Role role : CHANNEL.getGuild().getRoles()) {
						String roleMention = "@" + role.getName();
						String formattedMention = Formatting.YELLOW + "@" + role.getName() + Formatting.RESET;
						temp = StringUtils.replaceIgnoreCase(temp, roleMention, MarkdownSanitizer.escape(formattedMention));
					}
					temp = StringUtils.replaceIgnoreCase(temp, "@everyone", Formatting.YELLOW + "@everyone" + Formatting.RESET);
					temp = StringUtils.replaceIgnoreCase(temp, "@here", Formatting.YELLOW + "@here" + Formatting.RESET);

					referencedMessage = new StringBuilder(temp);
				}

				finalReferencedMessage = MarkdownParser.parseMarkdown(referencedMessage.toString().replace("\\", "\\\\"));

				for (String protocol : new String[]{"http://", "https://"}) {
					if (finalReferencedMessage.contains(protocol)) {
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
								hyperlinkInsert = textAfterPlaceholder[0] + "},{\"text\":\"<gif>\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder[0];
							} else {
								hyperlinkInsert = textAfterPlaceholder[0] + "},{\"text\":\"" + protocol + link + "\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder[0];
							}
							finalReferencedMessage = StringUtils.replaceIgnoreCase(finalReferencedMessage, (protocol + link), hyperlinkInsert);
						}
					}
				}
			}

			StringBuilder message = new StringBuilder(EmojiParser.parseToAliases(messageTemp));

			if (!e.getMessage().getAttachments().isEmpty()) {
				if (!messageTemp.isBlank()) {
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

			if (!e.getMessage().getStickers().isEmpty()) {
				if (!messageTemp.isBlank()) {
					message.append(" ");
				}
				for (int i = 0; i < e.getMessage().getStickers().size(); i++) {
					message.append(Formatting.YELLOW).append("<sticker>");
				}
			}

			if (StringUtils.countMatches(message, ":") >= 2) {
				String[] emojiNames = StringUtils.substringsBetween(message.toString(), ":", ":");
				for (String emojiName : emojiNames) {
					List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
					if (!emojis.isEmpty() || EmojiManager.getForAlias(emojiName) != null) {
						message = new StringBuilder(StringUtils.replaceIgnoreCase(message.toString(), (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.RESET)));
					}
				}
			}

			if (message.toString().contains("@")) {
				String temp = message.toString();

				for (Member member : CHANNEL.getMembers()) {
					String usernameMention = "@" + member.getUser().getName();
					String displayNameMention = "@" + member.getUser().getEffectiveName();
					String formattedMention = Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.RESET;
					temp = StringUtils.replaceIgnoreCase(temp, usernameMention, MarkdownSanitizer.escape(formattedMention));
					temp = StringUtils.replaceIgnoreCase(temp, displayNameMention, MarkdownSanitizer.escape(formattedMention));

					if (member.getNickname() != null) {
						String nicknameMention = "@" + member.getNickname();
						temp = StringUtils.replaceIgnoreCase(temp, nicknameMention, MarkdownSanitizer.escape(formattedMention));
					}
				}
				for (Role role : CHANNEL.getGuild().getRoles()) {
					String roleMention = "@" + role.getName();
					String formattedMention = Formatting.YELLOW + "@" + role.getName() + Formatting.RESET;
					temp = StringUtils.replaceIgnoreCase(temp, roleMention, MarkdownSanitizer.escape(formattedMention));
				}
				temp = StringUtils.replaceIgnoreCase(temp, "@everyone", Formatting.YELLOW + "@everyone" + Formatting.RESET);
				temp = StringUtils.replaceIgnoreCase(temp, "@here", Formatting.YELLOW + "@here" + Formatting.RESET);

				message = new StringBuilder(temp);
			}

			finalMessage = MarkdownParser.parseMarkdown(message.toString().replace("\\", "\\\\"));

			for (String protocol : new String[]{"http://", "https://"}) {
				if (finalMessage.contains(protocol)) {
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
							hyperlinkInsert = textAfterPlaceholder[1] + "},{\"text\":\"<gif>\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder[1];
						} else {
							hyperlinkInsert = textAfterPlaceholder[1] + "},{\"text\":\"" + protocol + link + "\",\"bold\":false,\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{" + textBeforePlaceholder[1];
						}
						finalMessage = StringUtils.replaceIgnoreCase(finalMessage, (protocol + link), hyperlinkInsert);
					}
				}
			}
		}

		if (CONFIG.generic.broadcastChatMessages) {
			if (e.getMessage().getReferencedMessage() != null) {
				String s = Translations.translateMessage("message.formattedResponseMessage");
				Text referenceFinalText = Text.Serializer.fromJson(s
						.replace("%message%", (CONFIG.generic.formatChatMessages ? finalReferencedMessage : EmojiParser.parseToAliases(referencedMessageTemp).replace("\"", "\\\""))
								.replace("\n", "\n" + textAfterPlaceholder[0] + "}," + s.substring(1, s.indexOf("%message%"))))
						.replace("%server%", "Discord")
						.replace("%name%", (referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()).replace("\\", "\\\\").replace("\"", "\\\"") : webhookName)
						.replace("%roleName%", referencedMemberRoleName)
						.replace("%roleColor%", "#" + Integer.toHexString((referencedMember != null) ? referencedMember.getColorRaw() : Role.DEFAULT_COLOR_RAW)));

				SERVER.getPlayerManager().getPlayerList().forEach(
						player -> player.sendMessage(referenceFinalText, false));
			}

			String s = Translations.translateMessage("message.formattedChatMessage");
			Text finalText = Text.Serializer.fromJson(s
					.replace("%message%", (CONFIG.generic.formatChatMessages ? finalMessage : EmojiParser.parseToAliases(messageTemp).replace("\"", "\\\""))
							.replace("\n", "\n" + textAfterPlaceholder[1] + "}," + s.substring(1, s.indexOf("%message%"))))
					.replace("%server%", "Discord")
					.replace("%name%", (CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName()).replace("\\", "\\\\").replace("\"", "\\\""))
					.replace("%roleName%", memberRoleName)
					.replace("%roleColor%", "#" + Integer.toHexString(e.getMember().getColorRaw())));

			SERVER.getPlayerManager().getPlayerList().forEach(
					player -> player.sendMessage(finalText, false));
		}
	}
}
