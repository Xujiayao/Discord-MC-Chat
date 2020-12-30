package io.gitee.xujiayao147.mcDiscordChatBridge.listeners;

import java.util.Optional;

import org.json.JSONObject;

import com.mashape.unirest.http.Unirest;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerAdvancementCallback;
import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerDeathCallback;
import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerJoinCallback;
import io.gitee.xujiayao147.mcDiscordChatBridge.events.PlayerLeaveCallback;
import io.gitee.xujiayao147.mcDiscordChatBridge.events.ServerChatCallback;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.MarkdownParser;
import io.gitee.xujiayao147.mcDiscordChatBridge.utils.Utils;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

/**
 * @author Xujiayao
 */
public class MinecraftEventListener {
	public void init() {
		ServerChatCallback.EVENT.register((playerEntity, rawMessage, message) -> {
			Pair<String, String> convertedPair = Utils.convertMentionsFromNames(rawMessage);
			if (Main.config.isWebhookEnabled) {
				JSONObject body = new JSONObject();
				body.put("username", playerEntity.getEntityName());
				body.put("avatar_url", "https://mc-heads.net/avatar/" + playerEntity.getEntityName());
				JSONObject allowed_mentions = new JSONObject();
				allowed_mentions.put("parse", new String[] { "users", "roles" });
				body.put("allowed_mentions", allowed_mentions);
				body.put("content", convertedPair.getLeft());
				try {
					Unirest.post(Main.config.webhookURL).header("Content-Type", "application/json").body(body)
							.asJsonAsync();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				Main.textChannel.sendMessage(
						Main.config.texts.playerMessage.replace("%playername%", playerEntity.getEntityName())
								.replace("%playermessage%", convertedPair.getLeft()))
						.queue();
			}
			if (Main.config.modifyChatMessages) {
				String jsonString = Text.Serializer.toJson(message);
				JSONObject newComponent = new JSONObject(jsonString);
				newComponent.getJSONArray("with").put(1, MarkdownParser.parseMarkdown(convertedPair.getRight()));
				Text finalText = Text.Serializer.fromJson(newComponent.toString());
				return Optional.ofNullable(finalText);
			}
			return Optional.empty();
		});

		PlayerAdvancementCallback.EVENT.register((playerEntity, advancement) -> {
			if (Main.config.announceAdvancements && advancement.getDisplay() != null
					&& advancement.getDisplay().shouldAnnounceToChat()
					&& playerEntity.getAdvancementTracker().getProgress(advancement).isDone()) {
				switch (advancement.getDisplay().getFrame()) {
				case GOAL:
					Main.textChannel.sendMessage(
							Main.config.texts.advancementGoal.replace("%playername%", playerEntity.getEntityName())
									.replace("%advancement%", advancement.getDisplay().getTitle().getString()))
							.queue();
					break;
				case TASK:
					Main.textChannel.sendMessage(
							Main.config.texts.advancementTask.replace("%playername%", playerEntity.getEntityName())
									.replace("%advancement%", advancement.getDisplay().getTitle().getString()))
							.queue();
					break;
				case CHALLENGE:
					Main.textChannel.sendMessage(
							Main.config.texts.advancementChallenge.replace("%playername%", playerEntity.getEntityName())
									.replace("%advancement%", advancement.getDisplay().getTitle().getString()))
							.queue();
					break;
				}
			}
		});

		PlayerDeathCallback.EVENT.register((playerEntity, damageSource) -> {
			if (Main.config.announceDeaths) {
				Main.textChannel.sendMessage(Main.config.texts.deathMessage
						.replace("%deathmessage%", damageSource.getDeathMessage(playerEntity).getString())
						.replace("%playername%", playerEntity.getEntityName())).queue();
			}
		});

		PlayerJoinCallback.EVENT.register((connection, playerEntity) -> {
			if (Main.config.announcePlayers) {
				Main.textChannel
						.sendMessage(Main.config.texts.joinServer.replace("%playername%", playerEntity.getEntityName()))
						.queue();
			}
		});

		PlayerLeaveCallback.EVENT.register((playerEntity) -> {
			if (Main.config.announcePlayers) {
				Main.textChannel
						.sendMessage(Main.config.texts.leftServer.replace("%playername%", playerEntity.getEntityName()))
						.queue();
			}
		});
	}
}
