package com.xujiayao.discord_mc_chat.fabric.mixins;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TellRawCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(TellRawCommand.class)
public class MixinTellRawCommand {

	@Inject(method = "method_13777", at = @At("HEAD"))
	private static void method_13777(CommandContext<CommandSourceStack> commandContext, CallbackInfoReturnable<Integer> cir) {
		// PlayerSay Event
		LOGGER.info("[DMCC] Player {} said: {}", commandContext.getSource().getDisplayName().getString(), commandContext);
	}
}
