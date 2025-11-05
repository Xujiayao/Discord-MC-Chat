package com.xujiayao.discord_mc_chat.client.mixins;

import com.xujiayao.discord_mc_chat.minecraft.MinecraftEvents;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Xujiayao
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "broadcastChatMessage", at = @At("HEAD"))
	private void broadcastChatMessage(PlayerChatMessage playerChatMessage, CallbackInfo ci) {
		// PlayerChat Event
		EventManager.post(new MinecraftEvents.PlayerChat(
				playerChatMessage,
				player,
				ci
		));
	}

	@Inject(method = "performUnsignedChatCommand", at = @At("HEAD"))
	private void performUnsignedChatCommand(String string, CallbackInfo ci) {
		// PlayerCommand Event
		EventManager.post(new MinecraftEvents.PlayerCommand(
				string,
				player,
				ci
		));
	}

	@Inject(method = "performSignedChatCommand", at = @At("HEAD"))
	private void performSignedChatCommand(ServerboundChatCommandSignedPacket serverboundChatCommandSignedPacket, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		// PlayerCommand Event
		EventManager.post(new MinecraftEvents.PlayerCommand(
				serverboundChatCommandSignedPacket.command(),
				player,
				ci
		));
	}
}
