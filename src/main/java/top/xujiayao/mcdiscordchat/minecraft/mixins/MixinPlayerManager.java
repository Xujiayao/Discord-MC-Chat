package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.message.MessageType;
//#if MC >= 11900
import net.minecraft.network.message.SignedMessage;
//#endif
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
//#if MC < 11900
//$$ import net.minecraft.text.Text;
//#endif
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.utils.Translations;
import top.xujiayao.mcdiscordchat.utils.Utils;

//#if MC < 11900
//$$ import java.util.UUID;
//#endif

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.WEBHOOK;

/**
 * @author Xujiayao
 */
@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	//#if MC >= 11903
	@Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("RETURN"))
	private void broadcast(SignedMessage message, ServerCommandSource source, MessageType.Parameters params, CallbackInfo ci) {
		sendMessage(message.getSignedContent(), source.getName());
	}
	//#elseif MC >= 11900
	//$$ @Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("RETURN"))
	//$$ private void broadcast(SignedMessage message, ServerCommandSource source, MessageType.Parameters params, CallbackInfo ci) {
	//$$  sendMessage(message.getSignedContent().plain(), source.getName());
	//$$ }
	//#elseif MC >= 11800
	//$$ @Inject(method = "broadcast", at = @At("RETURN"))
	//$$ private void broadcast(Text message, MessageType type, UUID sender, CallbackInfo ci) {
	//$$  sendMessage(message.getString(), "Server");
	//$$ }
	//#elseif MC >= 11600
	//$$ @Inject(method = "broadcastChatMessage", at = @At("RETURN"))
	//$$ private void broadcastChatMessage(Text message, MessageType type, UUID sender, CallbackInfo ci) {
	//$$  sendMessage(message.getString(), "Server");
	//$$ }
	//#else
	//$$ @Inject(method = "sendToAll(Lnet/minecraft/text/Text;)V", at = @At("RETURN"))
	//$$ private void sendToAll(Text text, CallbackInfo ci) {
	//$$  sendMessage(text.getString(), "Server");
	//$$ }
	//#endif

	private void sendMessage(String content, String username) {
		if (CONFIG.generic.broadcastChatMessages) {
			if (!CONFIG.generic.useWebhook) {
				CHANNEL.sendMessage(((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] <") : "<") + username + "> " + content).queue();
			} else {
				JsonObject body = new JsonObject();
				body.addProperty("content", content);
				body.addProperty("username", ((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + username) : username));
				body.addProperty("avatar_url", JDA.getSelfUser().getAvatarUrl());

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

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, true, false, username, content);
			}
		}
	}

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		Utils.setBotActivity();

		if (CONFIG.generic.announcePlayerJoinLeave) {
			CHANNEL.sendMessage(Translations.translateMessage("message.joinServer")
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.joinServer")
						.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName())));
			}
		}
	}

	@Inject(method = "remove", at = @At("RETURN"))
	private void remove(ServerPlayerEntity player, CallbackInfo ci) {
		Utils.setBotActivity();

		if (CONFIG.generic.announcePlayerJoinLeave) {
			CHANNEL.sendMessage(Translations.translateMessage("message.leftServer")
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.leftServer")
						.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName())));
			}
		}
	}
}

