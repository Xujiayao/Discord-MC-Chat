package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;

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
		} else {
			if (message.getRaw().startsWith("/")) {
				executeCommand(message.getRaw());
			} else {
				player.updateLastActionTime();

				String contentToDiscord = message.getRaw();
				String contentToMinecraft = message.getRaw();

				// TODO 处理Markdown（contentToMinecraft）

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

				if (contentToDiscord.contains("@")) {
					String[] memberNames = StringUtils.substringsBetween(contentToDiscord, "@", " ");
					if (!StringUtils.substringAfterLast(contentToDiscord, "@").contains(" ")) {
						memberNames = ArrayUtils.add(memberNames, StringUtils.substringAfterLast(contentToDiscord, "@"));
					}
					for (String memberName : memberNames) {
						for (Member member : CHANNEL.getMembers()) {
							if (member.getUser().getName().equalsIgnoreCase(memberName)
									|| (member.getNickname() != null && member.getNickname().equalsIgnoreCase(memberName))) {
								contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, ("@" + memberName), member.getAsMention());
								contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, ("@" + memberName), (Formatting.YELLOW + "@" + member.getEffectiveName() + Formatting.WHITE));
							}
						}
					}
				}

				json.getAsJsonArray("with").add(contentToMinecraft);
				Text finalText = Text.Serializer.fromJson(json.toString());
				server.getPlayerManager().broadcast(finalText, MessageType.CHAT, player.getUuid());
				ci.cancel();

				sendWebhookMessage(contentToDiscord, false);
			}

			messageCooldown += 20;
			if (messageCooldown > 200 && !server.getPlayerManager().isOperator(player.getGameProfile())) {
				disconnect(new TranslatableText("disconnect.spam"));
			}
		}

		ci.cancel();
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

				sendWebhookMessage(input, true);
			}
		}
	}

	private void sendWebhookMessage(String content, boolean escapeMarkdown) {
		JsonObject body = new JsonObject();
		body.addProperty("content", (escapeMarkdown ? MarkdownSanitizer.escape(content) : content));
		// TODO MultiServer
		//body.addProperty("username", (CONFIG.generic.multiServerMode ? ("[" + CONFIG.multiServer.serverDisplayName + "] " + playerEntity.getEntityName() : playerEntity.getEntityName()));
		body.addProperty("username", player.getEntityName());
		body.addProperty("avatar_url", CONFIG.generic.avatarApi.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUuid().toString() : player.getEntityName())));

		try {
			Request request = new Request.Builder()
					.url(CONFIG.generic.webhookUrl)
					.post(RequestBody.create(body.toString(), MediaType.get("application/json")))
					.build();

			HTTP_CLIENT.newCall(request).execute();
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}
