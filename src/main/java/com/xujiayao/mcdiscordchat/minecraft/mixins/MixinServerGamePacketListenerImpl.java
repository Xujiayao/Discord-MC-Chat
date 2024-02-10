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
//#if MC < 11605
//$$ import net.minecraft.network.protocol.game.ServerboundChatPacket;
//#endif
import net.minecraft.server.level.ServerPlayer;
//#if MC == 11900
//$$ import net.minecraft.server.network.FilteredText;
//#endif
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//#if MC >= 11605 && MC <= 11802
//$$ import net.minecraft.server.network.TextFilter;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC == 11900
//$$ import java.util.Objects;
//#endif
import java.util.Optional;

import static com.xujiayao.mcdiscordchat.Main.SERVER;

/**
 * @author Xujiayao
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

	@Shadow
	private ServerPlayer player;

	//#if MC > 11900
	@Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;)V", at = @At("HEAD"), cancellable = true)
	private void broadcastChatMessage(PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, playerChatMessage.decoratedContent().getString());
		if (result.isPresent()) {
			SERVER.getPlayerList().broadcastChatMessage(playerChatMessage.withUnsignedContent(result.get()), this.player, ChatType.bind(ChatType.CHAT, player));
			ci.cancel();
		}
	}
	//#elseif MC == 11900
	//$$ @Inject(method = "broadcastChatMessage(Lnet/minecraft/server/network/FilteredText;)V", at = @At("HEAD"), cancellable = true)
	//$$ private void broadcastChatMessage(FilteredText<PlayerChatMessage> filteredText, CallbackInfo ci) {
	//$$ 	Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, Objects.requireNonNull(filteredText.filtered()).serverContent().getString());
	//$$ 	if (result.isPresent()) {
	//$$ 		SERVER.getPlayerList().broadcastChatMessage(FilteredText.passThrough(filteredText.filtered().withUnsignedContent(result.get())), this.player, ChatType.CHAT);
	//$$ 		ci.cancel();
	//$$ 	}
	//$$ }
	//#elseif MC > 11605
	//$$ @Inject(method = "handleChat(Lnet/minecraft/server/network/TextFilter$FilteredText;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V"), cancellable = true)
	//$$ private void handleChat(TextFilter.FilteredText filteredText, CallbackInfo ci) {
	//$$ 	Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, filteredText.getFiltered());
	//$$ 	if (result.isPresent()) {
	//$$ 		Component component = new TranslatableComponent("chat.type.text", this.player.getDisplayName(), result.get());
	//$$ 		SERVER.getPlayerList().broadcastMessage(component, ChatType.CHAT, this.player.getUUID());
	//$$ 		ci.cancel();
	//$$ 	}
	//$$ }
	//#elseif MC == 11605
	//$$ @Inject(method = "handleChat(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V"), cancellable = true)
	//$$ private void handleChat(String string, CallbackInfo ci) {
	//$$ 	Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, string);
	//$$ 	if (result.isPresent()) {
	//$$ 		Component component = new TranslatableComponent("chat.type.text", this.player.getDisplayName(), result.get());
	//$$ 		SERVER.getPlayerList().broadcastMessage(component, ChatType.CHAT, this.player.getUUID());
	//$$ 		ci.cancel();
	//$$ 	}
	//$$ }
	//#else
	//$$ @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastMessage(Lnet/minecraft/network/chat/Component;Z)V"), cancellable = true)
	//$$ private void handleChat(ServerboundChatPacket serverboundChatPacket, CallbackInfo ci) {
	//$$ 	Optional<Component> result = MinecraftEvents.PLAYER_MESSAGE.invoker().message(player, serverboundChatPacket.getMessage());
	//$$ 	if (result.isPresent()) {
	//$$ 		Component component = new TranslatableComponent("chat.type.text", this.player.getDisplayName(), result.get());
	//$$ 		SERVER.getPlayerList().broadcastMessage(component, false);
	//$$ 		ci.cancel();
	//$$ 	}
	//$$ }
	//#endif

	//#if MC >= 11903
	@Inject(method = "performChatCommand(Lnet/minecraft/network/protocol/game/ServerboundChatCommandPacket;Lnet/minecraft/network/chat/LastSeenMessages;)V", at = @At("HEAD"))
	private void performChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	}
	//#elseif MC > 11900
	//$$ @Inject(method = "performChatCommand(Lnet/minecraft/network/protocol/game/ServerboundChatCommandPacket;)V", at = @At("HEAD"))
	//$$ private void performChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, CallbackInfo ci) {
	//$$ 	MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	//$$ }
	//#elseif MC == 11900
	//$$ @Inject(method = "handleChatCommand(Lnet/minecraft/network/protocol/game/ServerboundChatCommandPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I"))
	//$$ private void handleChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, CallbackInfo ci) {
	//$$  MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	//$$ }
	//#else
	//$$ @Inject(method = "handleCommand(Ljava/lang/String;)V", at = @At("HEAD"))
	//$$ private void handleCommand(String string, CallbackInfo ci) {
	//$$  MinecraftEvents.PLAYER_COMMAND.invoker().command(player, string);
	//$$ }
	//#endif
}
