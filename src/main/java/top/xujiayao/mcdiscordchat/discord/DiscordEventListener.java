package top.xujiayao.mcdiscordchat.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.multiServer.MultiServer;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CHANNEL_TOPIC_MONITOR_TIMER;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MSPT_MONITOR_TIMER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

public class DiscordEventListener extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
		e.deferReply().queue();

		if (!e.isFromGuild() || (e.getChannel() != CHANNEL)) {
			e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You can only use this command on the correct channel!**" : "**你只能在正确的频道使用此命令！**").queue();
			return;
		}

		switch (e.getName()) {
			case "info" -> {
				e.getHook().sendMessage(Utils.getInfoCommandMessage()).queue();
				MULTI_SERVER.sendMessage(true, false, null, "{\"type\":\"info\"}");
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
					/log                 | Get the latest server log (admin only)
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
					/log                 | 获取服务器最新日志（仅限管理员）
					/stop                | 停止服务器（仅限管理员）
					```""").queue();
			case "update" -> e.getHook().sendMessage(Utils.checkUpdate(true)).queue();
			case "stats" -> {
				StringBuilder message = new StringBuilder()
						.append("```\n=============== ")
						.append(CONFIG.generic.useEngInsteadOfChin ? "Scoreboard" : "排行榜")
						.append(" ===============\n\n");

				Map<String, Integer> stats = new HashMap<>();

				try {
					JsonArray players = new Gson().fromJson(IOUtils.toString(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath() + "/usercache.json").toURI(), StandardCharsets.UTF_8), JsonArray.class);

					FileUtils.listFiles(new File((SERVER.getSaveProperties().getLevelName() + "/stats/")), null, false).forEach(file -> {
						try {
							for (JsonElement player : players) {
								if (player.getAsJsonObject().get("uuid").getAsString().equals(file.getName().replace(".json", ""))) {
									JsonObject json = new Gson().fromJson(IOUtils.toString(file.toURI(), StandardCharsets.UTF_8), JsonObject.class);

									try {
										stats.put(player.getAsJsonObject().get("name").getAsString(), json
												.getAsJsonObject("stats")
												.getAsJsonObject("minecraft:" + Objects.requireNonNull(e.getOption("type")).getAsString())
												.get("minecraft:" + Objects.requireNonNull(e.getOption("name")).getAsString())
												.getAsInt());
									} catch (NullPointerException ignored) {
									}
								}
							}
						} catch (Exception ex) {
							LOGGER.error(ExceptionUtils.getStackTrace(ex));
						}
					});
				} catch (Exception ex) {
					LOGGER.error(ExceptionUtils.getStackTrace(ex));
				}

				if (stats.isEmpty()) {
					message.append(CONFIG.generic.useEngInsteadOfChin ? "No result" : "无结果");
				} else {
					List<Map.Entry<String, Integer>> sortedlist = new ArrayList<>(stats.entrySet());
					sortedlist.sort((c1, c2) -> c2.getValue().compareTo(c1.getValue()));

					for (Map.Entry<String, Integer> entry : sortedlist) {
						message.append(String.format("%-8d %-8s\n", entry.getValue(), entry.getKey()));
					}
				}

				message.append("```");
				e.getHook().sendMessage(message.toString()).queue();
			}
			case "reload" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					try {
						if (CONFIG.multiServer.enable) {
							MULTI_SERVER.bye();
							MULTI_SERVER.stopMultiServer();
						}

						MSPT_MONITOR_TIMER.cancel();
						CHANNEL_TOPIC_MONITOR_TIMER.cancel();

						ConfigManager.init(true);

						Utils.testJsonValid();

						Utils.setBotActivity();

						CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
						if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
							CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
						}

						Utils.updateBotCommands();

						if (CONFIG.generic.announceHighMspt) {
							MSPT_MONITOR_TIMER = new Timer();
							Utils.initMsptMonitor();
						}

						if (CONFIG.generic.updateChannelTopic) {
							CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
							Utils.initChannelTopicMonitor();
						}

						if (CONFIG.multiServer.enable) {
							MULTI_SERVER = new MultiServer();
							MULTI_SERVER.start();
						}

						e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Config file reloaded successfully!**" : "**配置文件重新加载成功！**").queue();
					} catch (Exception ex) {
						LOGGER.error(ExceptionUtils.getStackTrace(ex));
						e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**Config file reload failed!**" : "**配置文件重新加载失败！**").queue();
					}
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
										.execute(SERVER.getCommandSource().withOutput(new DiscordCommandOutput()), command));
					}
				} else {
					e.getHook().sendMessage(CONFIG.generic.useEngInsteadOfChin ? "**You do not have permission to use this command!**" : "**你没有权限使用此命令！**").queue();
				}
			}
			case "log" -> {
				if (CONFIG.generic.adminsIds.contains(Objects.requireNonNull(e.getMember()).getId())) {
					e.getHook().sendFile(new File(FabricLoader.getInstance().getGameDir().toFile(), "logs/latest.log")).queue();
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
	public void onMessageReceived(MessageReceivedEvent e) {
		if ((e.getChannel() != CHANNEL)
				|| (e.getAuthor() == JDA.getSelfUser())
				|| (e.getAuthor().isBot())) {
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

			Utils.sendConsoleMessage(TEXTS.unformattedResponseMessage()
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

		Utils.sendConsoleMessage(TEXTS.unformattedChatMessage()
				.replace("%server%", "Discord")
				.replace("%name%", CONFIG.generic.useServerNickname ? Objects.requireNonNull(e.getMember()).getEffectiveName() : Objects.requireNonNull(e.getMember()).getUser().getName())
				.replace("%roleName%", memberRoleName)
				.replace("%message%", EmojiParser.parseToAliases(e.getMessage().getContentDisplay())));

		StringBuilder referencedMessage = new StringBuilder();

		if (e.getMessage().getReferencedMessage() != null) {
			referencedMessage = new StringBuilder(EmojiParser.parseToAliases(e.getMessage().getReferencedMessage().getContentDisplay()));

			if (!e.getMessage().getReferencedMessage().getAttachments().isEmpty()) {
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

			if (StringUtils.countMatches(referencedMessage, ":") >= 2) {
				String[] emoteNames = StringUtils.substringsBetween(referencedMessage.toString(), ":", ":");
				for (String emoteName : emoteNames) {
					List<Emote> emotes = JDA.getEmotesByName(emoteName, true);
					if (!emotes.isEmpty()) {
						referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.DARK_GRAY)));
					} else if (EmojiManager.getForAlias(emoteName) != null) {
						referencedMessage = new StringBuilder(StringUtils.replaceIgnoreCase(referencedMessage.toString(), (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.DARK_GRAY)));
					}
				}
			}

			if (referencedMessage.toString().contains("@")) {
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
		}

		StringBuilder message = new StringBuilder(EmojiParser.parseToAliases(e.getMessage().getContentDisplay()));

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

		if (e.getMessage().getReferencedMessage() != null) {
			Text referenceFinalText = Text.Serializer.fromJson(TEXTS.formattedResponseMessage()
					.replace("%server%", "Discord")
					.replace("%name%", (referencedMember != null) ? (CONFIG.generic.useServerNickname ? referencedMember.getEffectiveName() : referencedMember.getUser().getName()) : webhookName)
					.replace("%roleName%", referencedMemberRoleName)
					.replace("%roleColor%", "#" + Integer.toHexString((referencedMember != null) ? referencedMember.getColorRaw() : Role.DEFAULT_COLOR_RAW))
					.replace("%message%", MarkdownParser.parseMarkdown(referencedMessage.toString())));

			SERVER.getPlayerManager().getPlayerList().forEach(
					player -> player.sendMessage(referenceFinalText, false));
		}

		Text finalText = Text.Serializer.fromJson(TEXTS.formattedChatMessage()
				.replace("%server%", "Discord")
				.replace("%name%", CONFIG.generic.useServerNickname ? e.getMember().getEffectiveName() : e.getMember().getUser().getName())
				.replace("%roleName%", memberRoleName)
				.replace("%roleColor%", "#" + Integer.toHexString(Objects.requireNonNull(e.getMember()).getColorRaw()))
				.replace("%message%", MarkdownParser.parseMarkdown(message.toString())));

		SERVER.getPlayerManager().getPlayerList().forEach(
				player -> player.sendMessage(finalText, false));
	}
}
