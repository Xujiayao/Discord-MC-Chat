package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.EmoteCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(EmoteCommands.class)
final class MixinEmoteCommands {

	@Inject(method = "lambda$register$1", at = @At("HEAD"), cancellable = true)
	private static void lambda$register$1(CommandContext<CommandSourceStack> commandContext, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		// SourceMe Event
		EventManager.post(new MinecraftEvents.SourceMe(
				commandContext,
				playerChatMessage
		));

		if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
			ci.cancel();
		}
	}
}
