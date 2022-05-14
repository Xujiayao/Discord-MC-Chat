package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.client.option.ChatVisibility;
import net.minecraft.network.MessageType;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.filter.TextStream.Message;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
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

import java.util.ArrayList;
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

	@Shadow
	private int messageCooldown;

	@Shadow
	public abstract void sendPacket(Packet<?> packet);

	@Shadow
	public abstract void executeCommand(String input);

	@Shadow
	public abstract void disconnect(Text reason);

	@Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
	private void handleMessage(Message message, CallbackInfo ci) {
		if (player.getClientChatVisibility() == ChatVisibility.HIDDEN) {
			sendPacket(new GameMessageS2CPacket((new TranslatableText("chat.disabled.options")).formatted(Formatting.RED), MessageType.SYSTEM, Util.NIL_UUID));
			ci.cancel();
		} else {
			player.updateLastActionTime();

			if (message.getRaw().startsWith("/")) {
				executeCommand(message.getRaw());
				ci.cancel();
			} else {
				String contentToDiscord = message.getRaw();
				String contentToMinecraft = message.getRaw();

				Text text = new TranslatableText("chat.type.text", player.getDisplayName(), contentToDiscord);
				JsonObject json = new Gson().fromJson(Text.Serializer.toJson(text), JsonObject.class);
				json.getAsJsonArray("with").remove(1);

				if (StringUtils.countMatches(contentToDiscord, ":") >= 2) {
					String[] emoteNames = StringUtils.substringsBetween(contentToDiscord, ":", ":");
					for (String emoteName : emoteNames) {
						List<Emote> emotes = JDA.getEmotesByName(emoteName, true);
						if (!emotes.isEmpty()) {
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, (":" + emoteName + ":"), emotes.get(0).getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.WHITE));
						} else if (EmojiManager.getForAlias(emoteName) != null) {
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emoteName + ":"), (Formatting.YELLOW + ":" + emoteName + ":" + Formatting.WHITE));
						}
					}
				}

				if (CONFIG.generic.allowMentions) {
					if (contentToDiscord.contains("@")) {
						String[] names = StringUtils.substringsBetween(contentToDiscord, "@", " ");
						if (!StringUtils.substringAfterLast(contentToDiscord, "@").contains(" ")) {
							names = ArrayUtils.add(names, StringUtils.substringAfterLast(contentToDiscord, "@"));
						}
						for (String name : names) {
							for (Member member : CHANNEL.getMembers()) {
								if (member.getUser().getName().equalsIgnoreCase(name)
										|| (member.getNickname() != null && member.getNickname().equalsIgnoreCase(name))) {
									contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, ("@" + name), member.getAsMention());
									contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, ("@" + name), (Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.WHITE));
								}
							}
							for (Role role : CHANNEL.getGuild().getRoles()) {
								if (role.getName().equalsIgnoreCase(name)) {
									contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, ("@" + name), role.getAsMention());
									contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, ("@" + name), (Formatting.YELLOW + "@" + role.getName() + Formatting.WHITE));
								}
							}
						}
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@everyone", (Formatting.YELLOW + "@everyone" + Formatting.WHITE));
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@here", (Formatting.YELLOW + "@here" + Formatting.WHITE));
					}
				}

				if (CONFIG.generic.modifyChatMessages) {
					contentToMinecraft = MarkdownParser.parseMarkdown(contentToMinecraft);

					if (contentToMinecraft.contains("http://")) {
						String[] links = StringUtils.substringsBetween(contentToMinecraft, "http://", " ");
						if (!StringUtils.substringAfterLast(contentToMinecraft, "http://").contains(" ")) {
							links = ArrayUtils.add(links, StringUtils.substringAfterLast(contentToMinecraft, "http://"));
						}
						for (String link : links) {
							if (link.contains("\n")) {
								link = StringUtils.substringBefore(link, "\n");
							}

							String hyperlinkInsert = "\"},{\"text\":\"http://" + link + "\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + "http://" + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, ("http://" + link), hyperlinkInsert);
						}
					}

					if (contentToMinecraft.contains("https://")) {
						String[] links = StringUtils.substringsBetween(contentToMinecraft, "https://", " ");
						if (!StringUtils.substringAfterLast(contentToMinecraft, "https://").contains(" ")) {
							links = ArrayUtils.add(links, StringUtils.substringAfterLast(contentToMinecraft, "https://"));
						}

						for (String link : links) {
							if (link.contains("\n")) {
								link = StringUtils.substringBefore(link, "\n");
							}

							String hyperlinkInsert = "\"},{\"text\":\"https://" + link + "\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + "https://" + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, ("https://" + link), hyperlinkInsert);
						}
					}

					json.getAsJsonArray("with").add(new Gson().fromJson(("[{\"text\":\"" + contentToMinecraft + "\"}]"), JsonArray.class));
					Text finalText = Text.Serializer.fromJson(json.toString());
					server.getPlayerManager().broadcastChatMessage(finalText, MessageType.CHAT, player.getUuid());
					ci.cancel();
				}

				sendWebhookMessage(contentToDiscord, false);
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, player.getEntityName(), contentToMinecraft);
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
		if (CONFIG.generic.broadcastCommandExecution && !CONFIG.generic.excludedCommands.contains(input)) {
			if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
				MINECRAFT_SEND_COUNT = 0;
				MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
			}

			MINECRAFT_SEND_COUNT++;
			if (MINECRAFT_SEND_COUNT <= 20) {
				Text text = new LiteralText("<").append(player.getEntityName()).append("> ").append(input);

				List<ServerPlayerEntity> list = new ArrayList<>(server.getPlayerManager().getPlayerList());
				list.remove(player);
				list.forEach(serverPlayerEntity -> serverPlayerEntity.sendMessage(text, false));

				SERVER.sendSystemMessage(text, player.getUuid());

				sendWebhookMessage(input, true);
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, player.getEntityName(), MarkdownSanitizer.escape(input));
				}
			}
		}
	}

	private void sendWebhookMessage(String content, boolean escapeMarkdown) {
		JsonObject body = new JsonObject();
		body.addProperty("content", (escapeMarkdown ? MarkdownSanitizer.escape(content) : content));
		body.addProperty("username", (CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + player.getEntityName()) : player.getEntityName());
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
