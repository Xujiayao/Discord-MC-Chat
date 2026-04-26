package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
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
final class MixinMsgCommand {

	@Unique
	private static final Pattern MSG_PATTERN = Pattern.compile("^(?:msg|tell|w) @a .*");

	@Inject(method = "lambda$register$0", at = @At("HEAD"), cancellable = true)
	private static void lambda$register$0(CommandContext<CommandSourceStack> c, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
		if (MSG_PATTERN.matcher(c.getInput()).matches()) {
			MessageArgument.resolveChatMessage(c, "message", playerChatMessage -> {
				// SourceMsg Event
				EventManager.post(new MinecraftEvents.SourceMsg(
						c,
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
