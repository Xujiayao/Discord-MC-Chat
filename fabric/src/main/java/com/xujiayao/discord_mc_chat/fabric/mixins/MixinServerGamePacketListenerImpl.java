package com.xujiayao.discord_mc_chat.fabric.mixins;

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

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

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
		LOGGER.info("[DMCC] Player {} said: {}", player.getDisplayName().getString(), playerChatMessage.decoratedContent().getString());
	}

	@Inject(method = "performUnsignedChatCommand", at = @At("HEAD"))
	private void performUnsignedChatCommand(String string, CallbackInfo ci) {
		// PlayerCommand Event
		 LOGGER.info("[DMCC] Player {} executed command: {}", player.getDisplayName().getString(), string);
	}

	@Inject(method = "performSignedChatCommand", at = @At("HEAD"))
	private void performSignedChatCommand(ServerboundChatCommandSignedPacket serverboundChatCommandSignedPacket, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		// PlayerCommand Event
		LOGGER.info("[DMCC] Player {} executed command: {}", player.getDisplayName().getString(), serverboundChatCommandSignedPacket.command());
	}
}
