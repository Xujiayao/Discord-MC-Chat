package com.xujiayao.discord_mc_chat.client.mixins;

import com.mojang.brigadier.CommandDispatcher;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(Commands.class)
public class MixinCommands {

	@Shadow
	@Final
	private CommandDispatcher<CommandSourceStack> dispatcher;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void init(Commands.CommandSelection commandSelection, CommandBuildContext commandBuildContext, CallbackInfo ci) {
		// CommandRegister Event
		EventManager.post(new MinecraftEvents.CommandRegister(
				dispatcher,
				ci
		));
	}
}
