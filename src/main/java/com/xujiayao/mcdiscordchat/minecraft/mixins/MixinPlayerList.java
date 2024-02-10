package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
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
//$$ import static com.xujiayao.mcdiscordchat.Main.SERVER;
//#endif

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
	//#elseif MC == 11900
	//$$ @Inject(method = "broadcastChatMessage(Lnet/minecraft/server/network/FilteredText;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
	//$$ private void broadcastChatMessage(FilteredText<PlayerChatMessage> filteredText, CommandSourceStack commandSourceStack, ResourceKey<ChatType> resourceKey, CallbackInfo ci) {
	//$$ 	MinecraftEvents.SERVER_MESSAGE.invoker().message(filteredText.filtered().serverContent().getString(), commandSourceStack);
	//$$ 	// TODO filtered() or raw() ?
	//$$ }
	//#elseif MC > 11502
	//$$ @Inject(method = "broadcastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V", at = @At("HEAD"))
	//$$ private void broadcastMessage(Component component, ChatType chatType, UUID uUID, CallbackInfo ci) {
	//$$ 	// TODO Check if need (uuid == Util.NIL_UUID)
	//$$ 	MinecraftEvents.SERVER_MESSAGE.invoker().message(component.getString(), SERVER.createCommandSourceStack());
	//$$ }
	//#else
	//$$ @Inject(method = "broadcastMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
	//$$ private void broadcastMessage(Component component, CallbackInfo ci) {
	//$$ 	MinecraftEvents.SERVER_MESSAGE.invoker().message(component.getString(), SERVER.createCommandSourceStack());
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
