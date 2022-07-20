package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.SharedConstants;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
	public abstract void checkForSpam();

	@Shadow
	public abstract void handleMessage(ChatMessageC2SPacket packet, FilteredMessage<String> message);

	@Shadow
	public abstract void filterText(String text, Consumer<FilteredMessage<String>> consumer);

	@Shadow
	public abstract boolean canAcceptMessage(String string, Instant instant);

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
		if (hasIllegalCharacter(packet.getChatMessage())) {
			return;
		}

		if (canAcceptMessage(packet.getChatMessage(), packet.getTimestamp())) {
			String contentToDiscord = packet.getChatMessage();
			String contentToMinecraft = packet.getChatMessage();

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

			contentToMinecraft = MarkdownParser.parseMarkdown(contentToMinecraft);

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
				server.getPlayerManager().broadcast(FilteredMessage.permitted(SignedMessage.of(Text.Serializer.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]"))), player, MessageType.CHAT);
			} else {
				filterText(packet.getChatMessage(), (message) -> handleMessage(packet, message));
			}

			sendMessage(contentToDiscord, false);
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), CONFIG.generic.formatChatMessages ? contentToMinecraft : packet.getChatMessage());
			}
		}

		ci.cancel();
	}

	@Inject(method = "onCommandExecution", at = @At(value = "HEAD"), cancellable = true)
	private void onCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
		if (hasIllegalCharacter(packet.command())) {
			return;
		}

		if (canAcceptMessage(packet.command(), packet.timestamp())) {
			ServerCommandSource serverCommandSource = player.getCommandSource().withSigner(packet.createArgumentsSigner(player.getUuid()));
			server.getCommandManager().execute(serverCommandSource, packet.command());
			checkForSpam();

			if (CONFIG.generic.broadcastCommandExecution && !CONFIG.generic.excludedCommands.contains(packet.command())) {
				if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
					MINECRAFT_SEND_COUNT = 0;
					MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
				}

				MINECRAFT_SEND_COUNT++;
				if (MINECRAFT_SEND_COUNT <= 20) {
					Text text = Text.of("<" + player.getEntityName() + "> /" + packet.command());

					List<ServerPlayerEntity> list = new ArrayList<>(server.getPlayerManager().getPlayerList());
					list.forEach(serverPlayerEntity -> serverPlayerEntity.sendMessage(text, false));

					SERVER.sendMessage(text);

					sendMessage("/" + packet.command(), true);
					if (CONFIG.multiServer.enable) {
						MULTI_SERVER.sendMessage(false, true, false, player.getEntityName(), MarkdownSanitizer.escape("/" + packet.command()));
					}
				}
			}
		}

		ci.cancel();
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
