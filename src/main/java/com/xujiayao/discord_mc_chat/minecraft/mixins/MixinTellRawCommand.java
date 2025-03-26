package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ComponentArgument;
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

	@Inject(method = "method_13777(Lcom/mojang/brigadier/context/CommandContext;)I", at = @At("HEAD"))
	private static void method_13777(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
		String input = context.getInput();

		if (input.startsWith("/tellraw @a ") || input.startsWith("tellraw @a ")) {
			//#if MC >= 12105
			MinecraftEvents.COMMAND_MESSAGE.invoker().message(ComponentArgument.getRawComponent(context, "message").getString(), context.getSource());
			//#else
			//$$ MinecraftEvents.COMMAND_MESSAGE.invoker().message(ComponentArgument.getComponent(context, "message").getString(), context.getSource());
			//#endif
		}
	}
}
