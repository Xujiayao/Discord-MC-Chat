package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.xujiayao.mcdiscordchat.minecraft.MinecraftEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
//#if MC > 11900
import net.minecraft.network.chat.LastSeenMessages;
//#endif
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
//#if MC <= 11900
//$$ import net.minecraft.server.network.FilteredText;
//#endif
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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

	//#if MC > 11900
	@Inject(method = "performChatCommand", at = @At("HEAD"))
	private void performChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	}
	//#else
	//$$ @Inject(method = "handleChatCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V"))
	//$$ private void handleChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket, CallbackInfo ci) {
	//$$  MinecraftEvents.PLAYER_COMMAND.invoker().command(player, "/" + serverboundChatCommandPacket.command());
	//$$ }
	//#endif
}
