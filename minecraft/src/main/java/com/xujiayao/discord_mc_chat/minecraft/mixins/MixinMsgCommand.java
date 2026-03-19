package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.xujiayao.discord_mc_chat.client.ClientRuntimePolicy;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * @author Xujiayao
 */
@Mixin(MsgCommand.class)
public class MixinMsgCommand {

	@Inject(method = "lambda$register$0", at = @At("HEAD"))
	private static void lambda$register$0(CommandContext<CommandSourceStack> commandContext, Collection<ServerPlayer> collection, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		// SourceMsg Event
		EventManager.post(new MinecraftEvents.SourceMsg(
				commandContext,
				playerChatMessage
		));
		if (ClientRuntimePolicy.shouldCancelLocalSourceMessages()) {
			ci.cancel();
		}
	}
}
