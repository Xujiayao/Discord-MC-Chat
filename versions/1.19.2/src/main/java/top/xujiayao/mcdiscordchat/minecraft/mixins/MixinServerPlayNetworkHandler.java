//#if MC >= 11900
package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fellbaum.jemoji.EmojiManager;
import net.minecraft.SharedConstants;
import net.minecraft.network.message.FilterMask;
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
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;
import top.xujiayao.mcdiscordchat.utils.Translations;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
import static top.xujiayao.mcdiscordchat.Main.WEBHOOK;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinServerPlayNetworkHandler {

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
	public abstract boolean canAcceptMessage(SignedMessage message);

	@Shadow
	public abstract boolean canAcceptMessage(String message, Instant timestamp, LastSeenMessageList.Acknowledgment acknowledgment);

	@Shadow
	public abstract CompletableFuture<FilteredMessage> filterText(String text);

	@Shadow
	public abstract void handleCommandExecution(CommandExecutionC2SPacket packet);

	@Shadow
	public abstract SignedMessage getSignedMessage(ChatMessageC2SPacket packet);

	@Shadow
	public abstract void handleDecoratedMessage(SignedMessage message);

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
		if (hasIllegalCharacter(packet.chatMessage())) {
			disconnect(Text.translatable("multiplayer.disconnect.illegal_characters"));
		} else {
			if (canAcceptMessage(packet.chatMessage(), packet.timestamp(), packet.acknowledgment())) {
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
					SignedMessage signedMessage = getSignedMessage(packet);
					server.getPlayerManager().broadcast(signedMessage.withUnsignedContent(Objects.requireNonNull(Text.Serializer.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]"))), player, MessageType.params(MessageType.CHAT, player));
				} else {
					server.submit(() -> {
						SignedMessage signedMessage = getSignedMessage(packet);
						if (canAcceptMessage(signedMessage)) {
							messageChainTaskQueue.append(() -> {
								CompletableFuture<FilteredMessage> completableFuture = filterText(signedMessage.getSignedContent().plain());
								CompletableFuture<SignedMessage> completableFuture2 = server.getMessageDecorator().decorate(player, signedMessage);
								return CompletableFuture.allOf(completableFuture, completableFuture2).thenAcceptAsync((void_) -> {
									FilterMask filterMask = completableFuture.join().mask();
									SignedMessage signedMessage2 = completableFuture2.join().withFilterMask(filterMask);
									handleDecoratedMessage(signedMessage2);
								}, server);
							});
						}
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
				if (canAcceptMessage(packet.command(), packet.timestamp(), packet.acknowledgment())) {
					server.submit(() -> {
						handleCommandExecution(packet);
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