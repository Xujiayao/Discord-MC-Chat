package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
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
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;

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
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.WHITE));
						} else if (EmojiManager.getForAlias(emojiName) != null) {
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.WHITE));
						}
					}
				}

				if (CONFIG.generic.allowMentions && contentToDiscord.contains("@")) {
					List<String> parsedList = new ArrayList<>();
					Pattern pattern = Pattern.compile("@[^@]*?#\\d{4}");
					Matcher matcher = pattern.matcher(contentToDiscord);
					while (matcher.find()) {
						String tagMention = matcher.group();
						Member member = CHANNEL.getGuild().getMemberByTag(tagMention.substring(1));
						if (member != null) {
							parsedList.add(member.getUser().getName());
							parsedList.add(member.getEffectiveName());

							String formattedMention = Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.WHITE;
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, tagMention, member.getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, tagMention, MarkdownSanitizer.escape(formattedMention));
						}
					}

					for (Member member : CHANNEL.getMembers()) {
						if (parsedList.contains(member.getUser().getName()) || parsedList.contains(member.getEffectiveName())) {
							continue;
						}

						String usernameMention = "@" + member.getUser().getName();
						String formattedMention = Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.WHITE;

						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, usernameMention, member.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, usernameMention, MarkdownSanitizer.escape(formattedMention));

						if (member.getNickname() != null) {
							String nicknameMention = "@" + member.getNickname();
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, nicknameMention, member.getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, nicknameMention, MarkdownSanitizer.escape(formattedMention));
						}
					}
					for (Role role : CHANNEL.getGuild().getRoles()) {
						String roleMention = "@" + role.getName();
						String formattedMention = Formatting.YELLOW + "@" + role.getName() + Formatting.WHITE;
						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, roleMention, role.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, roleMention, MarkdownSanitizer.escape(formattedMention));
					}
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@everyone", Formatting.YELLOW + "@everyone" + Formatting.WHITE);
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@here", Formatting.YELLOW + "@here" + Formatting.WHITE);
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
						messageChainTaskQueue.append((executor) -> CompletableFuture.allOf(completableFuture, completableFuture2).thenAcceptAsync((void_) -> {
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

		if (CONFIG.generic.webhookUrl.isBlank()) {
			CHANNEL.sendMessage(((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] <") : "<") + player.getEntityName() + "> " + content).queue();
		} else {
			JsonObject body = new JsonObject();
			body.addProperty("content", content);
			body.addProperty("username", ((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + player.getEntityName()) : player.getEntityName()));
			body.addProperty("avatar_url", CONFIG.generic.avatarApi.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUuid().toString() : player.getEntityName())));
			if (!CONFIG.generic.allowMentions) {
				body.add("allowed_mentions", new Gson().fromJson("{\"parse\":[]}", JsonObject.class));
			}

			Request request = new Request.Builder()
					.url(CONFIG.generic.webhookUrl)
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
