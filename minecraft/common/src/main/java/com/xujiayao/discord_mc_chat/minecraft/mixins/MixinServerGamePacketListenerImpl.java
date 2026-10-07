package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.events.EventManager;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEvents;
import com.xujiayao.discord_mc_chat.minecraft.linking.PlayerFreezeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
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
		EventManager.post(new MinecraftEvents.PlayerChat(
				message,
				player
		));

		if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
			ci.cancel();
		}
	}

	/**
	 * A player who is held until they link a Discord account must not move. Dropping the packet also
	 * drops the vanilla correction, so the client is pulled back explicitly.
	 */
	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void dmccBlockFrozenMovement(CallbackInfo ci) {
		if (PlayerFreezeManager.isFrozen(player)) {
			ci.cancel();
			PlayerFreezeManager.correctIfNeeded(player);
		}
	}

	/**
	 * Everything a frozen player could use to change the world, their inventory or another entity.
	 * Chat and commands are not in this list on purpose: the reply that explains how to link has to
	 * reach the player, and {@code /dmcc link} has to stay usable. Breaking and placing blocks are
	 * injected separately, because those two are also undone on the client.
	 */
	@Inject(method = {
			"handleUseItem",
			"handleAttack",
			"handleInteract",
			"handleSetCarriedItem",
			"handleSetCreativeModeSlot",
			"handleContainerClick",
			"handleContainerButtonClick",
			"handlePlaceRecipe",
			"handleMoveVehicle",
			"handlePaddleBoat",
			"handlePlayerInput",
			"handlePlayerCommand",
			"handleTeleportToEntityPacket"
	}, at = @At("HEAD"), cancellable = true, require = 13)
	private void dmccBlockFrozenActions(CallbackInfo ci) {
		if (PlayerFreezeManager.isFrozen(player)) {
			ci.cancel();
		}
	}

	/**
	 * A dropped block change has to be undone on the client as well: the client predicts the block
	 * being broken or placed, and without this correction the world would only look right again
	 * after a relog.
	 */
	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void dmccBlockFrozenPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (PlayerFreezeManager.isFrozen(player)) {
			switch (packet.getAction()) {
				case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK ->
						resendBlock(packet.getPos());
				default -> {
				}
			}
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void dmccBlockFrozenUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
		if (PlayerFreezeManager.isFrozen(player)) {
			resendBlock(packet.hitResult().getBlockPos());
			ci.cancel();
		}
	}

	private void resendBlock(BlockPos pos) {
		player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
	}

	@Inject(method = "performUnsignedChatCommand", at = @At("HEAD"))
	private void performUnsignedChatCommand(String command, CallbackInfo ci) {
		postPlayerCommand(command);
	}

	@Inject(method = "performSignedChatCommand", at = @At("HEAD"))
	private void performSignedChatCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
		postPlayerCommand(packet.command());
	}

	private void postPlayerCommand(String command) {
		EventManager.post(new MinecraftEvents.PlayerCommand(
				command,
				player
		));

		// No need to cancel, because vanilla Minecraft does not broadcast commands
	}
}
