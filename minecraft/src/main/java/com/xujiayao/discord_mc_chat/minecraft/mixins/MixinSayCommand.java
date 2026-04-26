package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.SayCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(SayCommand.class)
final class MixinSayCommand {

	@Inject(method = "lambda$register$1", at = @At("HEAD"), cancellable = true)
	private static void lambda$register$1(CommandContext<CommandSourceStack> c, PlayerChatMessage message, CallbackInfo ci) {
		// SourceSay Event
		EventManager.post(new MinecraftEvents.SourceSay(
				c,
				message
		));

		if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
			ci.cancel();
		}
	}
}
