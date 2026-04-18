package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
@Mixin(TellRawCommand.class)
final class MixinTellRawCommand {

	@Unique
	private static final Pattern TELLRAW_PATTERN = Pattern.compile("^tellraw @a .*");

	@Inject(method = "lambda$register$0", at = @At("HEAD"), cancellable = true)
	private static void lambda$register$0(CommandContext<CommandSourceStack> c, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
		if (TELLRAW_PATTERN.matcher(c.getInput()).matches()) {
			Optional<ServerPlayer> optional = EntityArgument.getPlayers(c, "targets").stream().findFirst();
			if (optional.isPresent()) {
				ServerPlayer player = optional.get();
				Component component = ComponentArgument.getResolvedComponent(c, "message", player);

				// SourceTellRaw Event
				EventManager.post(new MinecraftEvents.SourceTellRaw(
						c,
						component
				));

				if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
					cir.setReturnValue(1);
					cir.cancel();
				}
			}
		}
	}
}
