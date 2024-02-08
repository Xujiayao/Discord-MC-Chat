package com.xujiayao.mcdiscordchat.minecraft;

import com.xujiayao.mcdiscordchat.utils.Translations;
import com.xujiayao.mcdiscordchat.utils.Utils;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.world.level.GameRules;

import java.util.Objects;

import static com.xujiayao.mcdiscordchat.Main.CHANNEL;
import static com.xujiayao.mcdiscordchat.Main.CONFIG;
import static com.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
public class MinecraftEventListener {

	public static void init() {
		MinecraftEvents.PLAYER_ADVANCEMENT.register((player, advancementHolder, isDone) -> {
			if (CONFIG.generic.announceAdvancements
					&& isDone
					&& advancementHolder.value().display().isPresent()
					&& advancementHolder.value().display().get().shouldAnnounceChat()
					&& player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
				String message = "null";

				switch (advancementHolder.value().display().get().getType()) {
					case GOAL -> message = Translations.translateMessage("message.advancementGoal");
					case TASK -> message = Translations.translateMessage("message.advancementTask");
					case CHALLENGE -> message = Translations.translateMessage("message.advancementChallenge");
				}

				String title = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".title");
				String description = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".description");

				message = message
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))
						.replace("%advancement%", title.contains("TranslateError") ? advancementHolder.value().display().get().getTitle().getString() : title)
						.replace("%description%", description.contains("TranslateError") ? advancementHolder.value().display().get().getDescription().getString() : description);

				CHANNEL.sendMessage(message).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
			}
		});

		MinecraftEvents.PLAYER_DIE.register((player, source) -> {
			if (CONFIG.generic.announceDeathMessages) {
				System.out.println(source.getLocalizedDeathMessage(player));
			}
		});

		// TODO Server /say

		MinecraftEvents.PLAYER_JOIN.register(player -> {
			Utils.setBotActivity();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.joinServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.joinServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});

		MinecraftEvents.PLAYER_QUIT.register(player -> {
			Utils.setBotActivity();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.leftServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.leftServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});
	}
}
