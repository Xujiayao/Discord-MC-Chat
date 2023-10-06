package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.xujiayao.mcdiscordchat.utils.Translations;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class MixinPlayerAdvancementTracker {

	@Shadow
	private ServerPlayerEntity owner;

	@Shadow
	public abstract AdvancementProgress getProgress(AdvancementEntry advancement);

	@Inject(method = "grantCriterion", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;onStatusUpdate(Lnet/minecraft/advancement/AdvancementEntry;)V"))
	private void grantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (CONFIG.generic.announceAdvancements
				&& getProgress(advancement).isDone()
				&& advancement.value().display().isPresent()
				&& advancement.value().display().get().shouldAnnounceToChat()
				&& owner.getWorld().getGameRules().getBoolean(GameRules.ANNOUNCE_ADVANCEMENTS)) {
			String message = "null";

			switch (advancement.value().display().get().getFrame()) {
				case GOAL -> message = Translations.translateMessage("message.advancementGoal");
				case TASK -> message = Translations.translateMessage("message.advancementTask");
				case CHALLENGE -> message = Translations.translateMessage("message.advancementChallenge");
			}

			String title = Translations.translate("advancements." + advancement.id().getPath().replace("/", ".") + ".title");
			String description = Translations.translate("advancements." + advancement.id().getPath().replace("/", ".") + ".description");

			message = message
					.replace("%playerName%", MarkdownSanitizer.escape(owner.getEntityName()))
					.replace("%advancement%", title.contains("TranslateError") ? advancement.value().display().get().getTitle().getString() : title)
					.replace("%description%", description.contains("TranslateError") ? advancement.value().display().get().getDescription().getString() : description);

			CHANNEL.sendMessage(message).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, message);
			}
		}
	}
}
