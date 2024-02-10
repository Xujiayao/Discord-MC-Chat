package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
//#if MC > 11900
import net.minecraft.network.chat.LastSeenMessages;
//#endif
//#if MC >= 11900
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
//#endif
//#if MC <= 11802
//$$ import net.minecraft.network.chat.TranslatableComponent;
//#endif
import net.minecraft.server.level.ServerPlayer;
//#if MC == 11900
//$$ import net.minecraft.server.network.FilteredText;
//#endif
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//#if MC <= 11802
//$$ import net.minecraft.server.network.TextFilter;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

import static com.xujiayao.mcdiscordchat.Main.SERVER;

/**
 * @author Xujiayao
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

	@Shadow
	private ServerPlayer player;

	//#if MC > 11802
	@Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
	private void broadcastChatMessage(PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, playerChatMessage.decoratedContent().getString());
		if (result.isPresent()) {
			//#if MC > 11900
			SERVER.getPlayerList().broadcastChatMessage(playerChatMessage.withUnsignedContent(result.get()), this.player, ChatType.bind(ChatType.CHAT, player));
			//#else
			//$$ SERVER.getPlayerList().broadcastChatMessage(FilteredText.passThrough(playerChatMessage.withUnsignedContent(result.get())), this.player, ChatType.CHAT);
			//#endif
			ci.cancel();
		}
	}
	//#else
	//$$ @Inject(method = "handleChat(Lnet/minecraft/server/network/TextFilter$FilteredText;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastMessage(Lnet/minecraft/network/chat/Component;Ljava/util/Function;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V"), cancellable = true)
	//$$ private void handleChat(TextFilter.FilteredText filteredText, CallbackInfo ci) {
	//$$ 	Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, filteredText.getFiltered());
	//$$ 	if (result.isPresent()) {
	//$$ 		Component component = new TranslatableComponent("chat.type.text", this.player.getDisplayName(), filteredText.getFiltered());
	//$$ 		SERVER.getPlayerList().broadcastMessage(component, ChatType.CHAT, this.player.getUUID());
	//$$ 		ci.cancel();
	//$$ 	}
	//$$ }
	//#endif

	//#if MC > 11900
	@Inject(method = "performChatCommand", at = @At("HEAD"))
	private void performChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	}
	//#elseif MC > 11802
	//$$ @Inject(method = "handleChatCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V"))
	//$$ private void handleChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, CallbackInfo ci) {
	//$$  MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	//$$ }
	//#else
	//$$ @Inject(method = "handleCommand", at = @At("HEAD"))
	//$$ private void handleCommand(String string, CallbackInfo ci) {
	//$$  MinecraftEvents.PLAYER_COMMAND.invoker().command(player, string);
	//$$ }
	//#endif
}
