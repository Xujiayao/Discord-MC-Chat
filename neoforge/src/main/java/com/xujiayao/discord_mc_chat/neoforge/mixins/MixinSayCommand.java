package com.xujiayao.discord_mc_chat.neoforge.mixins;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.server.commands.SayCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(SayCommand.class)
public class MixinSayCommand {

	@Inject(method = "lambda$register$1", at = @At("HEAD"))
	private static void lambda$register$1(CommandContext<CommandSourceStack> commandContext, CallbackInfoReturnable<Integer> cir) {
		// PlayerSay Event
		LOGGER.info("Player {} said: {}", commandContext.getSource().getTextName(), commandContext.getArgument("message", MessageArgument.class));
	}
}
