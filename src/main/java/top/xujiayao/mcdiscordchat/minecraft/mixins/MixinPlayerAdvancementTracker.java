package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
@Mixin(PlayerAdvancementTracker.class)
public class MixinPlayerAdvancementTracker {

	@Shadow
	private ServerPlayerEntity owner;

	@Inject(method = "grantCriterion", at = @At("HEAD"))
	private void grantCriterion(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceToChat()) {
			switch (advancement.getDisplay().getFrame()) {
				case GOAL -> CHANNEL.sendMessage(TEXTS.advancementGoal()
						.replace("%playerName%", MarkdownSanitizer.escape(owner.getEntityName()))
						.replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
				case TASK -> CHANNEL.sendMessage(TEXTS.advancementTask()
						.replace("%playerName%", MarkdownSanitizer.escape(owner.getEntityName()))
						.replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
				case CHALLENGE -> CHANNEL.sendMessage(TEXTS.advancementChallenge()
						.replace("%playerName%", MarkdownSanitizer.escape(owner.getEntityName()))
						.replace("%advancement%", advancement.getDisplay().getTitle().getString())).queue();
			}
		}
	}
}
