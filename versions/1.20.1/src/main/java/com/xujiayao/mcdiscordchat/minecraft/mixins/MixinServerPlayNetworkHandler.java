//#if MC >= 11903
package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fellbaum.jemoji.EmojiManager;
import net.minecraft.SharedConstants;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.message.LastSeenMessageList;
import net.minecraft.network.message.MessageChain;
import net.minecraft.network.message.MessageChainTaskQueue;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.xujiayao.mcdiscordchat.utils.MarkdownParser;
import com.xujiayao.mcdiscordchat.utils.Translations;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinServerPlayNetworkHandler implements EntityTrackingListener, ServerPlayPacketListener {

	@Shadow
	private ServerPlayerEntity player;

	@Final
	@Shadow
	private MinecraftServer server;

	@Final
	@Shadow
	private MessageChainTaskQueue messageChainTaskQueue;

	@Shadow
	public abstract void checkForSpam();

	@Shadow
	public abstract void disconnect(Text reason);

	@Shadow
	public abstract CompletableFuture<FilteredMessage> filterText(String text);

	@Shadow
	public abstract Optional<LastSeenMessageList> validateMessage(String message, Instant timestamp, LastSeenMessageList.Acknowledgment acknowledgment);

	@Shadow
	public abstract void handleCommandExecution(CommandExecutionC2SPacket packet, LastSeenMessageList lastSeenMessages);

	@Shadow
	public abstract SignedMessage getSignedMessage(ChatMessageC2SPacket packet, LastSeenMessageList lastSeenMessages) throws MessageChain.MessageChainException;

	@Shadow
	public abstract void handleDecoratedMessage(SignedMessage message);

	@Shadow
	public abstract void handleMessageChainException(MessageChain.MessageChainException exception);

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
		if (hasIllegalCharacter(packet.chatMessage())) {
			disconnect(Text.translatable("multiplayer.disconnect.illegal_characters"));
		} else {
			Optional<LastSeenMessageList> optional = validateMessage(packet.chatMessage(), packet.timestamp(), packet.acknowledgment());
			if (optional.isPresent()) {
				String contentToDiscord = packet.chatMessage();
				String contentToMinecraft = packet.chatMessage();

				if (StringUtils.countMatches(contentToDiscord, ":") >= 2) {
					String[] emojiNames = StringUtils.substringsBetween(contentToDiscord, ":", ":");
					for (String emojiName : emojiNames) {
						List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
						if (!emojis.isEmpty()) {
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, (":" + emojiName + ":"), emojis.get(0).getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.RESET));
						} else if (EmojiManager.getByAlias(emojiName).isPresent()) {
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.RESET));
						}
					}
				}

				if (!CONFIG.generic.allowedMentions.isEmpty() && contentToDiscord.contains("@")) {
					if (CONFIG.generic.allowedMentions.contains("users")) {
						for (Member member : CHANNEL.getMembers()) {
							String usernameMention = "@" + member.getUser().getName();
							String displayNameMention = "@" + member.getUser().getEffectiveName();
							String formattedMention = Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.RESET;

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
							String formattedMention = Formatting.YELLOW + "@" + role.getName() + Formatting.RESET;
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, roleMention, role.getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, roleMention, MarkdownSanitizer.escape(formattedMention));
						}
					}

					if (CONFIG.generic.allowedMentions.contains("everyone")) {
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@everyone", Formatting.YELLOW + "@everyone" + Formatting.RESET);
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@here", Formatting.YELLOW + "@here" + Formatting.RESET);
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

				if (CONFIG.generic.formatChatMessages) {
					try {
						SignedMessage signedMessage = getSignedMessage(packet, optional.get());
						server.getPlayerManager().broadcast(signedMessage.withUnsignedContent(Objects.requireNonNull(Text.Serializer.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]"))), player, MessageType.params(MessageType.CHAT, player));
					} catch (MessageChain.MessageChainException e) {
						handleMessageChainException(e);
					}
				} else {
					server.submit(() -> {
						SignedMessage signedMessage;
						try {
							signedMessage = getSignedMessage(packet, optional.get());
						} catch (MessageChain.MessageChainException var6) {
							handleMessageChainException(var6);
							return;
						}

						CompletableFuture<FilteredMessage> completableFuture = filterText(signedMessage.getSignedContent());
						CompletableFuture<Text> completableFuture2 = server.getMessageDecorator().decorate(player, signedMessage.getContent());
						messageChainTaskQueue.append(executor -> CompletableFuture.allOf(completableFuture, completableFuture2).thenAcceptAsync(void_ -> {
							SignedMessage signedMessage2 = signedMessage.withUnsignedContent(completableFuture2.join()).withFilterMask(completableFuture.join().mask());
							handleDecoratedMessage(signedMessage2);
						}, executor));
					});
				}

				if (CONFIG.generic.broadcastChatMessages) {
					sendMessage(contentToDiscord, false);
					if (CONFIG.multiServer.enable) {
						MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), CONFIG.generic.formatChatMessages ? contentToMinecraft : packet.chatMessage());
					}
				}
			}
		}

		ci.cancel();
	}

	@Inject(method = "onCommandExecution", at = @At(value = "HEAD"), cancellable = true)
	private void onCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
		if (CONFIG.generic.broadcastPlayerCommandExecution) {
			if (hasIllegalCharacter(packet.command())) {
				disconnect(Text.translatable("multiplayer.disconnect.illegal_characters"));
			} else {
				Optional<LastSeenMessageList> optional = validateMessage(packet.command(), packet.timestamp(), packet.acknowledgment());
				if (optional.isPresent()) {
					server.submit(() -> {
						handleCommandExecution(packet, optional.get());
						checkForSpam();
					});

					String input = "/" + packet.command();

					for (String command : CONFIG.generic.excludedCommands) {
						if (input.startsWith(command + " ")) {
							ci.cancel();
							return;
						}
					}

					if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
						MINECRAFT_SEND_COUNT = 0;
						MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
					}

					MINECRAFT_SEND_COUNT++;
					if (MINECRAFT_SEND_COUNT <= 20) {
						Text text = Text.of("<" + player.getEntityName() + "> " + input);

						server.getPlayerManager().getPlayerList().forEach(
								player -> player.sendMessage(text, false));

						SERVER.sendMessage(text);

						sendMessage(input, true);
						if (CONFIG.multiServer.enable) {
							MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), MarkdownSanitizer.escape(input));
						}
					}
				}
			}

			ci.cancel();
		}
	}

	private void sendMessage(String message, boolean escapeMarkdown) {
		String content = (escapeMarkdown ? MarkdownSanitizer.escape(message) : message);

		if (!CONFIG.generic.useWebhook) {
			if (CONFIG.multiServer.enable) {
				CHANNEL.sendMessage(Translations.translateMessage("message.messageWithoutWebhookForMultiServer")
						.replace("%server%", CONFIG.multiServer.name)
						.replace("%name%", player.getEntityName())
						.replace("%message%", content)).queue();
			} else {
				CHANNEL.sendMessage(Translations.translateMessage("message.messageWithoutWebhook")
						.replace("%name%", player.getEntityName())
						.replace("%message%", content)).queue();
			}
		} else {
			JsonObject body = new JsonObject();
			body.addProperty("content", content);
			body.addProperty("username", ((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + player.getEntityName()) : player.getEntityName()));
			body.addProperty("avatar_url", CONFIG.generic.avatarApi.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUuid().toString() : player.getEntityName())));

			JsonObject allowedMentions = new JsonObject();
			allowedMentions.add("parse", new Gson().toJsonTree(CONFIG.generic.allowedMentions).getAsJsonArray());
			body.add("allowed_mentions", allowedMentions);

			Request request = new Request.Builder()
					.url(WEBHOOK.getUrl())
					.post(RequestBody.create(body.toString(), MediaType.get("application/json")))
					.build();

			try {
				Response response = HTTP_CLIENT.newCall(request).execute();
				response.close();
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}

	private boolean hasIllegalCharacter(String message) {
		for (int i = 0; i < message.length(); ++i) {
			if (!SharedConstants.isValidChar(message.charAt(i))) {
				return true;
			}
		}

		return false;
	}
}
//#endif