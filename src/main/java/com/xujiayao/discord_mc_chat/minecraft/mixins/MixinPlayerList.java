package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
//#if MC < 11900
//$$ import net.minecraft.network.chat.Component;
//#endif
//#if MC >= 11900
import net.minecraft.network.chat.PlayerChatMessage;
//#endif
//#if MC > 11502 && MC <= 11900
//$$ import net.minecraft.resources.ResourceKey;
//#endif
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 12002
import net.minecraft.server.network.CommonListenerCookie;
//#endif
//#if MC == 11900
//$$ import net.minecraft.server.network.FilteredText;
//#endif
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC < 11900
//$$ import java.util.UUID;
//$$
//$$ import static com.xujiayao.discord_mc_chat.Main.SERVER;
//#endif

/**
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
public class MixinPlayerList {

	//#if MC > 11900
	@Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V", at = @At("HEAD"))
	private void broadcastChatMessage(PlayerChatMessage playerChatMessage, CommandSourceStack commandSourceStack, ChatType.Bound bound, CallbackInfo ci) {
		MinecraftEvents.COMMAND_MESSAGE.invoker().message(playerChatMessage.decoratedContent().getString(), commandSourceStack);
	}
	//#elseif MC == 11900
	//$$ @Inject(method = "broadcastChatMessage(Lnet/minecraft/server/network/FilteredText;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
	//$$ private void broadcastChatMessage(FilteredText<PlayerChatMessage> filteredText, CommandSourceStack commandSourceStack, ResourceKey<ChatType> resourceKey, CallbackInfo ci) {
	//$$ 	MinecraftEvents.COMMAND_MESSAGE.invoker().message(filteredText.filtered().serverContent().getString(), commandSourceStack);
	//$$ }
	//#endif
	// TODO This feature has been removed in versions 1.18.2 and below due to compatibility issues (#197)

	//#if MC >= 12002
	@Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V", at = @At("RETURN"))
	private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
	//#else
	//$$ @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
	//$$ private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CallbackInfo ci) {
	//#endif
		MinecraftEvents.PLAYER_JOIN.invoker().join(serverPlayer);
	}

	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
	private void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
		MinecraftEvents.PLAYER_QUIT.invoker().quit(serverPlayer);
	}
}
