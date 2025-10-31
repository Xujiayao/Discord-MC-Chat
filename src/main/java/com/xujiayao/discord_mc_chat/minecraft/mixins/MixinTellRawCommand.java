package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ComponentArgument;
//#if MC < 12105
//$$ import net.minecraft.network.chat.ComponentUtils;
//#endif
import net.minecraft.server.commands.TellRawCommand;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(TellRawCommand.class)
public class MixinTellRawCommand {

	@Inject(method = "method_13777(Lcom/mojang/brigadier/context/CommandContext;)I", at = @At("HEAD"))
	private static void method_13777(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
		String input = context.getInput();

		if (input.startsWith("/tellraw @a ") || input.startsWith("tellraw @a ")) {
			try {
				if (!CONFIG.generic.broadcastTellRawMessages) {
					return;
				}

				//#if MC >= 12105
				MinecraftEvents.COMMAND_MESSAGE.invoker().message(ComponentArgument.getResolvedComponent(context, "message").getString(), context.getSource());
				//#else
				//$$ MinecraftEvents.COMMAND_MESSAGE.invoker().message(ComponentUtils.updateForEntity(context.getSource(), ComponentArgument.getComponent(context, "message"), context.getSource().getEntity(), 0).getString(), context.getSource());
				//#endif
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}
}
