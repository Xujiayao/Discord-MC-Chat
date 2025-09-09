package com.xujiayao.discord_mc_chat.fabric.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.common.utils.events.EventManager;
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
public class MixinSayCommand {

	@Inject(method = "method_43657", at = @At("HEAD"))
	private static void method_43657(CommandContext<CommandSourceStack> commandContext, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		// SourceSay Event
		EventManager.post(new MinecraftEvents.SourceSay(
				commandContext,
				playerChatMessage,
				ci
		));
	}
}
