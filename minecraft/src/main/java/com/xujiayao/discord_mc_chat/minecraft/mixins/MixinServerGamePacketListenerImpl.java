package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
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
final class MixinServerGamePacketListenerImpl {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
	private void broadcastChatMessage(PlayerChatMessage message, CallbackInfo ci) {
		// PlayerChat Event
		EventManager.post(new MinecraftEvents.PlayerChat(
				message,
				player
		));

		if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
			ci.cancel();
		}
	}

	@Inject(method = "performUnsignedChatCommand", at = @At("HEAD"))
	private void performUnsignedChatCommand(String command, CallbackInfo ci) {
		// PlayerCommand Event
		EventManager.post(new MinecraftEvents.PlayerCommand(
				command,
				player
		));

		// No need to cancel, because vanilla Minecraft does not broadcast commands
	}

	@Inject(method = "performSignedChatCommand", at = @At("HEAD"))
	private void performSignedChatCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		// PlayerCommand Event
		EventManager.post(new MinecraftEvents.PlayerCommand(
				packet.command(),
				player
		));

		// No need to cancel, because vanilla Minecraft does not broadcast commands
	}
}
