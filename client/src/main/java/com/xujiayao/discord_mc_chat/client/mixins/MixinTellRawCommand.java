package com.xujiayao.discord_mc_chat.client.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TellRawCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author Xujiayao
 */
@Mixin(TellRawCommand.class)
public class MixinTellRawCommand {

	@Inject(method = "lambda$register$0", at = @At("HEAD"))
	private static void lambda$register$0(CommandContext<CommandSourceStack> commandContext, CallbackInfoReturnable<Integer> cir) {
		// SourceTellRaw Event
		EventManager.post(new MinecraftEvents.SourceTellRaw(
				commandContext,
				cir
		));
	}
}
