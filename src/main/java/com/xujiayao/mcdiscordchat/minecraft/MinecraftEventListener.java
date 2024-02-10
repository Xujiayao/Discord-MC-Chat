package com.xujiayao.mcdiscordchat.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xujiayao.mcdiscordchat.utils.MarkdownParser;
import com.xujiayao.mcdiscordchat.utils.Translations;
import com.xujiayao.mcdiscordchat.utils.Utils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fellbaum.jemoji.EmojiManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
//#if MC >= 11900
import net.minecraft.network.chat.ComponentContents;
//#endif
import net.minecraft.network.chat.MutableComponent;
//#if MC < 11900
//$$ import net.minecraft.network.chat.TextComponent;
//#endif
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameRules;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.mcdiscordchat.Main.CHANNEL;
import static com.xujiayao.mcdiscordchat.Main.CONFIG;
import static com.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static com.xujiayao.mcdiscordchat.Main.JDA;
import static com.xujiayao.mcdiscordchat.Main.LOGGER;
import static com.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static com.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;
import static com.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static com.xujiayao.mcdiscordchat.Main.SERVER;
import static com.xujiayao.mcdiscordchat.Main.WEBHOOK;

/**
 * @author Xujiayao
 */
public class MinecraftEventListener {

	public static void init() {
		MinecraftEvents.SERVER_MESSAGE.register((message, commandSourceStack) -> {
			sendDiscordMessage(message, commandSourceStack.getDisplayName().getString(), JDA.getSelfUser().getAvatarUrl());
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, true, false, commandSourceStack.getDisplayName().getString(), message);
			}
		});

		MinecraftEvents.PLAYER_MESSAGE.register((player, message) -> {
			String contentToDiscord = message;
			String contentToMinecraft = message;

			if (StringUtils.countMatches(contentToDiscord, ":") >= 2) {
				String[] emojiNames = StringUtils.substringsBetween(contentToDiscord, ":", ":");
				for (String emojiName : emojiNames) {
					List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
					if (!emojis.isEmpty()) {
						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, (":" + emojiName + ":"), emojis.get(0).getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (ChatFormatting.YELLOW + ":" + emojiName + ":" + ChatFormatting.RESET));
					} else if (EmojiManager.getByAlias(emojiName).isPresent()) {
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (ChatFormatting.YELLOW + ":" + emojiName + ":" + ChatFormatting.RESET));
					}
				}
			}

			if (!CONFIG.generic.allowedMentions.isEmpty() && contentToDiscord.contains("@")) {
				if (CONFIG.generic.allowedMentions.contains("users")) {
					for (Member member : CHANNEL.getMembers()) {
						String usernameMention = "@" + member.getUser().getName();
						String displayNameMention = "@" + member.getUser().getEffectiveName();
						String formattedMention = ChatFormatting.YELLOW + "@" + member.getEffectiveName() + ChatFormatting.RESET;

						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, usernameMention, member.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, usernameMention, MarkdownSanitizer.escape(formattedMention));

						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, displayNameMention, member.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, displayNameMention, MarkdownSanitizer.escape(formattedMention));

						if (member.getNickname() != null) {
							String nicknameMention = "@" + member.getNickname();
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, nicknameMention, member.getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, nicknameMention, MarkdownSanitizer.escape(formattedMention));
						}
					}
				}

				if (CONFIG.generic.allowedMentions.contains("roles")) {
					for (Role role : CHANNEL.getGuild().getRoles()) {
						String roleMention = "@" + role.getName();
						String formattedMention = ChatFormatting.YELLOW + "@" + role.getName() + ChatFormatting.RESET;
						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, roleMention, role.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, roleMention, MarkdownSanitizer.escape(formattedMention));
					}
				}

				if (CONFIG.generic.allowedMentions.contains("everyone")) {
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@everyone", ChatFormatting.YELLOW + "@everyone" + ChatFormatting.RESET);
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@here", ChatFormatting.YELLOW + "@here" + ChatFormatting.RESET);
				}
			}

			contentToMinecraft = MarkdownParser.parseMarkdown(contentToMinecraft.replace("\\", "\\\\"));

			for (String protocol : new String[]{"http://", "https://"}) {
				if (contentToMinecraft.contains(protocol)) {
					String[] links = StringUtils.substringsBetween(contentToMinecraft, protocol, " ");
					if (!StringUtils.substringAfterLast(contentToMinecraft, protocol).contains(" ")) {
						links = ArrayUtils.add(links, StringUtils.substringAfterLast(contentToMinecraft, protocol));
					}
					for (String link : links) {
						if (link.contains("\n")) {
							link = StringUtils.substringBefore(link, "\n");
						}

						String hyperlinkInsert;
						if (StringUtils.containsIgnoreCase(link, "gif")
								&& StringUtils.containsIgnoreCase(link, "tenor.com")) {
							hyperlinkInsert = "\"},{\"text\":\"<gif>\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
						} else {
							hyperlinkInsert = "\"},{\"text\":\"" + protocol + link + "\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
						}
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (protocol + link), hyperlinkInsert);
					}
				}
			}

			if (CONFIG.generic.broadcastChatMessages) {
				sendDiscordMessage(contentToDiscord, Objects.requireNonNull(player.getDisplayName()).getString(), CONFIG.generic.avatarApi.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUUID().toString() : player.getDisplayName().getString())));
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, false, Objects.requireNonNull(player.getDisplayName()).getString(), CONFIG.generic.formatChatMessages ? contentToMinecraft : message);
				}
			}

			if (CONFIG.generic.formatChatMessages) {
				return Optional.ofNullable(Component.Serializer.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]"));
			} else {
				return Optional.empty();
			}
		});

		MinecraftEvents.PLAYER_COMMAND.register((player, command) -> {
			for (String excludedCommand : CONFIG.generic.excludedCommands) {
				if (command.startsWith(excludedCommand + " ")) {
					return;
				}
			}

			if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
				MINECRAFT_SEND_COUNT = 0;
				MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
			}

			MINECRAFT_SEND_COUNT++;
			if (MINECRAFT_SEND_COUNT <= 20) {
				//#if MC >= 11900
				MutableComponent message = Component.literal("<" + Objects.requireNonNull(player.getDisplayName()).getString() + "> " + command);
				//#else
				//$$ MutableComponent message = new TextComponent("<" + Objects.requireNonNull(player.getDisplayName()).getString() + "> " + command);
				//#endif

				SERVER.getPlayerList().getPlayers().forEach(
						player1 -> player1.displayClientMessage(message, false));
				//#if MC >= 11900
				SERVER.sendSystemMessage(message);
				//#elseif MC > 11502
				//$$ SERVER.sendMessage(message, player.getUUID());
				//#else
				//$$ SERVER.sendMessage(message);
				//#endif

				sendDiscordMessage(MarkdownSanitizer.escape(command), Objects.requireNonNull(player.getDisplayName()).getString(), CONFIG.generic.avatarApi.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUUID().toString() : player.getDisplayName().getString())));
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, false, player.getDisplayName().getString(), MarkdownSanitizer.escape(command));
				}
			}
		});

		MinecraftEvents.PLAYER_ADVANCEMENT.register((player, advancementHolder, isDone) -> {
			//#if MC >= 12002
			if (advancementHolder.value().display().isEmpty()) {
				return;
			}
			DisplayInfo display = advancementHolder.value().display().get();
			//#else
			//$$ if (advancementHolder.getDisplay() == null) {
			//$$ 	return;
			//$$ }
			//$$ DisplayInfo display = advancementHolder.getDisplay();
			//#endif

			if (CONFIG.generic.announceAdvancements
					&& isDone
					&& display.shouldAnnounceChat()
					&& player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
				String message = "null";

				switch (display.getType()) {
					case GOAL -> message = Translations.translateMessage("message.advancementGoal");
					case TASK -> message = Translations.translateMessage("message.advancementTask");
					case CHALLENGE -> message = Translations.translateMessage("message.advancementChallenge");
				}

				String title = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".title");
				String description = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".description");

				message = message
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))
						.replace("%advancement%", title.contains("TranslateError") ? display.getTitle().getString() : title)
						.replace("%description%", description.contains("TranslateError") ? display.getDescription().getString() : description);

				CHANNEL.sendMessage(message).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
			}
		});

		MinecraftEvents.PLAYER_DIE.register((player, source) -> {
			if (CONFIG.generic.announceDeathMessages) {
				//#if MC >= 11900
				TranslatableContents deathMessage = (TranslatableContents) source.getLocalizedDeathMessage(player).getContents();
				//#else
				//$$ TranslatableComponent deathMessage = (TranslatableComponent) source.getLocalizedDeathMessage(player);
				//#endif
				String key = deathMessage.getKey();
				Object[] args = new String[deathMessage.getArgs().length];
				for (int i = 0; i < deathMessage.getArgs().length; i++) {
					Object object = deathMessage.getArgs()[i];
					if (object instanceof Component component) {
						//#if MC >= 11900
						ComponentContents componentContents = component.getContents();
						if (componentContents instanceof TranslatableContents) {
							args[i] = Translations.translate(((TranslatableContents) componentContents).getKey());
						//#else
						//$$ if (component instanceof TranslatableComponent) {
						//$$ 	args[i] = Translations.translate(((TranslatableComponent) component).getKey());
						//#endif
						} else {
							args[i] = component.getString();
						}
					} else {
						args[i] = object == null ? "null" : object.toString();
					}
				}

				CHANNEL.sendMessage(Translations.translateMessage("message.deathMessage")
						.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args)))
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.deathMessage")
							.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args)))
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});

		MinecraftEvents.PLAYER_JOIN.register(player -> {
			Utils.setBotActivity();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.joinServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.joinServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});

		MinecraftEvents.PLAYER_QUIT.register(player -> {
			Utils.setBotActivity();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.leftServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.leftServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});
	}

	private static void sendDiscordMessage(String content, String username, String avatar_url) {
		if (!CONFIG.generic.useWebhook) {
			if (CONFIG.multiServer.enable) {
				CHANNEL.sendMessage(Translations.translateMessage("message.messageWithoutWebhookForMultiServer")
						.replace("%server%", CONFIG.multiServer.name)
						.replace("%name%", username)
						.replace("%message%", content)).queue();
			} else {
				CHANNEL.sendMessage(Translations.translateMessage("message.messageWithoutWebhook")
						.replace("%name%", username)
						.replace("%message%", content)).queue();
			}
		} else {
			JsonObject body = new JsonObject();
			body.addProperty("content", content);
			body.addProperty("username", ((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + username) : username));
			body.addProperty("avatar_url", avatar_url);

			JsonObject allowedMentions = new JsonObject();
			allowedMentions.add("parse", new Gson().toJsonTree(CONFIG.generic.allowedMentions).getAsJsonArray());
			body.add("allowed_mentions", allowedMentions);

			Request request = new Request.Builder()
					.url(WEBHOOK.getUrl())
					.post(RequestBody.create(body.toString(), MediaType.get("application/json")))
					.build();

			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.submit(() -> {
				try {
					Response response = HTTP_CLIENT.newCall(request).execute();
					response.close();
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}
			});
			executor.shutdown();
		}
	}
}
