package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.server.commands.MsgCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
@Mixin(MsgCommand.class)
public class MixinMsgCommand {

	@Unique
	private static final Pattern MSG_PATTERN = Pattern.compile("^(?:msg|tell|w) @a .*");

	@Inject(method = "lambda$register$1", at = @At("HEAD"), cancellable = true)
	private static void lambda$register$1(CommandContext<CommandSourceStack> commandContext, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
		if (MSG_PATTERN.matcher(commandContext.getInput()).matches()) {
			MessageArgument.resolveChatMessage(commandContext, "message", playerChatMessage -> {
				// SourceMsg Event
				EventManager.post(new MinecraftEvents.SourceMsg(
						commandContext,
						playerChatMessage
				));
			});

			if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
				cir.setReturnValue(1);
				cir.cancel();
			}
		}
	}
}
