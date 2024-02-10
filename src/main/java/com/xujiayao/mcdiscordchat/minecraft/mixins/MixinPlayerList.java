package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
//#if MC <= 11900
//$$ import net.minecraft.resources.ResourceKey;
//#endif
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 12002
import net.minecraft.server.network.CommonListenerCookie;
//#endif
//#if MC <= 11900
//$$ import net.minecraft.server.network.FilteredText;
//#endif
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
public class MixinPlayerList {

	//#if MC > 11900
	@Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V", at = @At("HEAD"))
	private void broadcastChatMessage(PlayerChatMessage playerChatMessage, CommandSourceStack commandSourceStack, ChatType.Bound bound, CallbackInfo ci) {
		MinecraftEvents.SERVER_MESSAGE.invoker().message(playerChatMessage.decoratedContent().getString(), commandSourceStack);
	}
	//#else
	//$$ @Inject(method = "broadcastChatMessage", at = @At("HEAD"))
	//$$ private void broadcastChatMessage(FilteredText<PlayerChatMessage> filteredText, CommandSourceStack commandSourceStack, ResourceKey<ChatType> resourceKey, CallbackInfo ci) {
	//$$ 	MinecraftEvents.SERVER_MESSAGE.invoker().message(filteredText.filtered().serverContent().getString(), commandSourceStack);
	//$$ 	// TODO filtered() or raw() ?
	//$$ }
	//#endif

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	//#if MC >= 12002
	private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
	//#else
	//$$ private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CallbackInfo ci) {
	//#endif
		MinecraftEvents.PLAYER_JOIN.invoker().join(serverPlayer);
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
		MinecraftEvents.PLAYER_QUIT.invoker().quit(serverPlayer);
	}
}
