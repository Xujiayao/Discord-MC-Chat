package top.xujiayao.mcDiscordChat.listeners;

import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import top.xujiayao.mcDiscordChat.Config;
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

				if (Config.bannedMinecraft.contains(playerEntity.getEntityName())) {
					return Optional.empty();
				}

				JSONObject body = new JSONObject();
				body.put("username", playerEntity.getEntityName());
				body.put("avatar_url", "https://mc-heads.net/avatar/" + (Config.useUUIDInsteadNickname ? playerEntity.getUuid() : playerEntity.getEntityName()));

				JSONObject allowed_mentions = new JSONObject();
				allowed_mentions.put("parse", new String[]{"users", "roles"});

				body.put("allowed_mentions", allowed_mentions);
				body.put("content", convertedPair.getLeft().replace("_", "\\_"));

				try {
					Unirest.post(Config.webhookURL).header("Content-Type", "application/json").body(body).asJsonAsync();
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (Config.modifyChatMessages) {
					String jsonString = Text.Serializer.toJson(message);
					JSONObject newComponent = new JSONObject(jsonString);

					if (convertedPair.getRight().startsWith("!!")) {
						newComponent.getJSONArray("with").put(1, convertedPair.getRight());
					} else {
						newComponent.getJSONArray("with").put(1, MarkdownParser.parseMarkdown(convertedPair.getRight()));
					}

					Text finalText = Text.Serializer.fromJson(newComponent.toString());

					return Optional.ofNullable(finalText);
				}
			}

			return Optional.empty();
		});

		PlayerAdvancementCallback.EVENT.register((playerEntity, advancement) -> {
			if (Config.announceAdvancements && advancement.getDisplay() != null
				  && advancement.getDisplay().shouldAnnounceToChat()
				  && playerEntity.getAdvancementTracker().getProgress(advancement).isDone() && !Main.stop) {
				switch (advancement.getDisplay().getFrame()) {
					case GOAL:
						Main.textChannel.sendMessage(Config.advancementGoal
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
					case TASK:
						Main.textChannel.sendMessage(Config.advancementTask
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
					case CHALLENGE:
						Main.textChannel.sendMessage(Config.advancementChallenge
							  .replace("%playername%", playerEntity.getEntityName().replace("_", "\\_"))
							  .replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
						break;
				}
			}
		});

		PlayerDeathCallback.EVENT.register((playerEntity, damageSource) -> {
			if (Config.announceDeaths && !Main.stop) {
				Main.textChannel.sendMessage(Config.deathMessage
					  .replace("%deathmessage%", MarkdownSanitizer.escape(damageSource.getDeathMessage(playerEntity).getString()))
					  .replace("%playername%", MarkdownSanitizer.escape(playerEntity.getEntityName()).replace("_", "\\_"))).queue();
			}
		});

		PlayerJoinCallback.EVENT.register((connection, playerEntity) -> {
			if (Config.announcePlayers && !Main.stop) {
				Main.textChannel.sendMessage(Config.joinServer.replace("%playername%",
					  MarkdownSanitizer.escape(playerEntity.getEntityName()).replace("_", "\\_"))).queue();
			}
		});

		PlayerLeaveCallback.EVENT.register((playerEntity) -> {
			if (Config.announcePlayers && !Main.stop) {
				Main.textChannel.sendMessage(Config.leftServer.replace("%playername%",
					  MarkdownSanitizer.escape(playerEntity.getEntityName()).replace("_", "\\_"))).queue();
			}
		});
	}
}
