package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
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
public class MixinEmoteCommands {

	@Inject(method = "lambda$register$0", at = @At("HEAD"))
	private static void lambda$register$0(CommandContext<CommandSourceStack> commandContext, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		// SourceMe Event
		EventManager.post(new MinecraftEvents.SourceMe(
				commandContext,
				playerChatMessage
		));
	}
}
