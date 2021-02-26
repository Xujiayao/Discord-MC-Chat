package top.xujiayao.mcDiscordChat.listeners;

import com.mashape.unirest.http.Unirest;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import org.json.JSONObject;
import top.xujiayao.mcDiscordChat.Main;
import top.xujiayao.mcDiscordChat.events.PlayerAdvancementCallback;
import top.xujiayao.mcDiscordChat.events.PlayerDeathCallback;
import top.xujiayao.mcDiscordChat.events.PlayerJoinCallback;
import top.xujiayao.mcDiscordChat.events.PlayerLeaveCallback;
import top.xujiayao.mcDiscordChat.events.ServerChatCallback;
import top.xujiayao.mcDiscordChat.utils.MarkdownParser;
import top.xujiayao.mcDiscordChat.utils.Utils;

import java.util.Optional;

/**
 * @author Xujiayao
 */
public class MinecraftEventListener {
	public void init() {
		ServerChatCallback.EVENT.register((playerEntity, rawMessage, message) -> {
			if (!Main.stop) {
				Pair<String, String> convertedPair = Utils.convertMentionsFromNames(rawMessage);

				if (Main.config.bannedMinecraft.contains(playerEntity.getEntityName())) {
					return Optional.empty();
				}

				JSONObject body = new JSONObject();
				body.put("username", playerEntity.getEntityName());
				body.put("avatar_url", "https://mc-heads.net/avatar/" + playerEntity.getEntityName());

				JSONObject allowed_mentions = new JSONObject();
				allowed_mentions.put("parse", new String[]{"users", "roles"});

				body.put("allowed_mentions", allowed_mentions);
				body.put("content", convertedPair.getLeft().replace("_", "\\_"));

				try {
					Unirest.post(Main.config.webhookURL).header("Content-Type", "application/json").body(body).asJsonAsync();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				if (Main.config.modifyChatMessages) {
					String jsonString = Text.Serializer.toJson(message);
					JSONObject newComponent = new JSONObject(jsonString);
					newComponent.getJSONArray("with").put(1, MarkdownParser.parseMarkdown(convertedPair.getRight()));
					Text finalText = Text.Serializer.fromJson(newComponent.toString());

					return Optional.ofNullable(finalText);
				}
			}

			return Optional.empty();
		});

		PlayerAdvancementCallback.EVENT.register((playerEntity, advancement) -> {
			if (Main.config.announceAdvancements && advancement.getDisplay() != null
				  && advancement.getDisplay().shouldAnnounceToChat()
				  && playerEntity.getAdvancementTracker().getProgress(advancement).isDone() && !Main.stop) {
				switch (advancement.getDisplay().getFrame()) {
					case GOAL:
						Main.textChannel.sendMessage(Main.config.texts.advancementGoal
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
					case TASK:
						Main.textChannel.sendMessage(Main.config.texts.advancementTask
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
					case CHALLENGE:
						Main.textChannel.sendMessage(Main.config.texts.advancementChallenge
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
				}
			}
		});

		PlayerDeathCallback.EVENT.register((playerEntity, damageSource) -> {
			if (Main.config.announceDeaths && !Main.stop) {
				Main.textChannel.sendMessage(Main.config.texts.deathMessage
					  .replace("%deathmessage%", damageSource.getDeathMessage(playerEntity).getString())
					  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))).queue();
			}
		});

		PlayerJoinCallback.EVENT.register((connection, playerEntity) -> {
			if (Main.config.announcePlayers && !Main.stop) {
				Main.textChannel.sendMessage(Main.config.texts.joinServer.replace("%playername%",
					  playerEntity.getEntityName().replace("_", "\\_"))).queue();
			}
		});

		PlayerLeaveCallback.EVENT.register((playerEntity) -> {
			if (Main.config.announcePlayers && !Main.stop) {
				Main.textChannel.sendMessage(Main.config.texts.leftServer.replace("%playername%",
					  playerEntity.getEntityName().replace("_", "\\_"))).queue();
			}
		});
	}
}
