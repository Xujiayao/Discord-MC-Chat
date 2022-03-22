package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.filter.TextStream.Message;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
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

import java.util.List;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow
	private ServerPlayerEntity player;

	@Final
	@Shadow
	private MinecraftServer server;

	@Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
	private void handleMessage(Message message, CallbackInfo ci) {
//		if (config.generic.bannedMinecraft.contains(playerEntity.getEntityName())) {
//			return Optional.empty();
//		}

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

		// TODO Change colour for emoji too

		json.getAsJsonArray("with").add(contentToMinecraft);
		Text finalText = Text.Serializer.fromJson(json.toString());
		server.getPlayerManager().broadcast(finalText, MessageType.CHAT, this.player.getUuid());
		ci.cancel();

		JsonObject body = new JsonObject();
		body.addProperty("content", contentToDiscord);
		//body.addProperty("username", (CONFIG.generic.multiServerMode ? ("[" + config.multiServer.serverDisplayName + "] " + playerEntity.getEntityName() : playerEntity.getEntityName()));
		body.addProperty("username", player.getEntityName());
		body.addProperty("avatar_url", CONFIG.generic.avatarAPI.replace("%player%", (CONFIG.generic.useUuidInsteadOfName ? player.getUuid().toString() : player.getEntityName())));

		try {
			Request request = new Request.Builder()
					.url(CONFIG.generic.webhookURL)
					.post(RequestBody.create(body.toString(), MediaType.get("application/json")))
					.build();

			HTTP_CLIENT.newCall(request).execute();
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

//	@Inject(at = @At(value = "TAIL"), method = "executeCommand")
//	private void onCommandExecuted(String input, CallbackInfo ci) {
//		CommandExecutionCallback.EVENT.invoker().onExecuted(input, this.player.getCommandSource());
//	}
}
