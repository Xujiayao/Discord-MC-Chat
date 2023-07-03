package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.SharedConstants;
import net.minecraft.client.options.ChatVisibility;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
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

import java.util.List;

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
public abstract class MixinServerPlayNetworkHandler implements ServerPlayPacketListener {

	@Shadow
	private ServerPlayerEntity player;

	@Final
	@Shadow
	private MinecraftServer server;

	@Shadow
	private int messageCooldown;

	@Shadow
	public abstract void sendPacket(Packet<?> packet);

	@Shadow
	public abstract void executeCommand(String input);

	@Shadow
	public abstract void disconnect(Text reason);

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	public void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
		NetworkThreadUtils.forceMainThread(packet, this, player.getServerWorld());
		if (player.getClientChatVisibility() == ChatVisibility.HIDDEN) {
			sendPacket(new ChatMessageS2CPacket((new TranslatableText("chat.cannotSend")).formatted(Formatting.RED)));
			ci.cancel();
		} else {
			player.updateLastActionTime();
			String string = packet.getChatMessage();
			string = StringUtils.normalizeSpace(string);

			for (int i = 0; i < string.length(); ++i) {
				if (!SharedConstants.isValidChar(string.charAt(i))) {
					disconnect(new TranslatableText("multiplayer.disconnect.illegal_characters"));
					ci.cancel();
					return;
				}
			}

			if (string.startsWith("/")) {
				executeCommand(string);
				ci.cancel();
			} else {
				String contentToDiscord = string;
				String contentToMinecraft = string;

				if (StringUtils.countMatches(contentToDiscord, ":") >= 2) {
					String[] emojiNames = StringUtils.substringsBetween(contentToDiscord, ":", ":");
					for (String emojiName : emojiNames) {
						List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
						if (!emojis.isEmpty()) {
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, (":" + emojiName + ":"), emojis.get(0).getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (Formatting.YELLOW + ":" + emojiName + ":" + Formatting.RESET));
						} else if (EmojiManager.getForAlias(emojiName) != null) {
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
					server.getPlayerManager().broadcastChatMessage(new TranslatableText("chat.type.text", player.getDisplayName(), Text.Serializer.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]")), false);
					ci.cancel();
				}

				sendMessage(contentToDiscord, false);
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), CONFIG.generic.formatChatMessages ? contentToMinecraft : string);
				}
			}

			messageCooldown += 20;
			if (messageCooldown > 200 && !server.getPlayerManager().isOperator(player.getGameProfile())) {
				disconnect(new TranslatableText("disconnect.spam"));
			}
		}
	}

	@Inject(method = "executeCommand", at = @At(value = "HEAD"))
	private void executeCommand(String input, CallbackInfo ci) {
		if (CONFIG.generic.broadcastPlayerCommandExecution) {
			for (String command : CONFIG.generic.excludedCommands) {
				if (input.startsWith(command + " ")) {
					return;
				}
			}

			if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
				MINECRAFT_SEND_COUNT = 0;
				MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
			}

			MINECRAFT_SEND_COUNT++;
			if (MINECRAFT_SEND_COUNT <= 20) {
				Text text = new LiteralText("<").append(player.getEntityName()).append("> ").append(input);

				server.getPlayerManager().getPlayerList().forEach(
						player -> player.sendMessage(text));

				SERVER.sendMessage(text);

				sendMessage(input, true);
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), MarkdownSanitizer.escape(input));
				}
			}
		}
	}

	private void sendMessage(String message, boolean escapeMarkdown) {
		String content = (escapeMarkdown ? MarkdownSanitizer.escape(message) : message);

		if (!CONFIG.generic.useWebhook) {
			CHANNEL.sendMessage(((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] <") : "<") + player.getEntityName() + "> " + content).queue();
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
}
