package com.xujiayao.discord_mc_chat.minecraft.mixins;

//#if MC > 11902 || MC <= 11802
import com.mojang.brigadier.context.CommandContext;
//#endif
//#if MC <= 11802
//$$ import com.mojang.brigadier.exceptions.CommandSyntaxException;
//#endif
import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
//#if MC <= 11802
//$$ import net.minecraft.commands.arguments.MessageArgument;
//#endif
//#if MC > 11802
import net.minecraft.network.chat.PlayerChatMessage;
//#endif
import net.minecraft.server.commands.SayCommand;
//#if MC <= 11902 && MC > 11802
//$$ import net.minecraft.server.network.FilteredText;
//$$ import net.minecraft.server.players.PlayerList;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC <= 11802
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif

//#if MC <= 11900
//$$ import java.util.Objects;
//#endif

/**
 * @author Xujiayao
 */
@Mixin(SayCommand.class)
public class MixinSayCommand {

	//#if MC > 11902
	@Inject(method = "method_43657(Lcom/mojang/brigadier/context/CommandContext;Lnet/minecraft/network/chat/PlayerChatMessage;)V", at = @At("HEAD"))
	private static void method_43657(CommandContext<CommandSourceStack> context, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		MinecraftEvents.COMMAND_MESSAGE.invoker().message(playerChatMessage.decoratedContent().getString(), context.getSource());
	}
	//#elseif MC > 11900
	//$$ @Inject(method = "method_43657(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/PlayerChatMessage;)V", at = @At("HEAD"))
	//$$ private static void method_43657(PlayerList playerList, CommandSourceStack commandSourceStack, PlayerChatMessage playerChatMessage, CallbackInfo ci) {
	//$$ 	MinecraftEvents.COMMAND_MESSAGE.invoker().message(playerChatMessage.serverContent().getString(), commandSourceStack);
	//$$ }
	//#elseif MC == 11900
	//$$ @Inject(method = "method_43657(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/network/FilteredText;)V", at = @At("HEAD"))
	//$$ private static void method_43657(PlayerList playerList, CommandSourceStack commandSourceStack, FilteredText<PlayerChatMessage> filteredText, CallbackInfo ci) {
	//$$ 	MinecraftEvents.COMMAND_MESSAGE.invoker().message(Objects.requireNonNull(filteredText.filtered()).serverContent().getString(), commandSourceStack);
	//$$ }
	//#else
	//$$ @Inject(method = "method_13563(Lcom/mojang/brigadier/context/CommandContext;)I", at = @At("HEAD"))
	//$$ private static void method_43657(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
	//$$ 	MinecraftEvents.COMMAND_MESSAGE.invoker().message(MessageArgument.getMessage(context, "message").getString(), context.getSource());
	//$$ }
	//#endif
}
