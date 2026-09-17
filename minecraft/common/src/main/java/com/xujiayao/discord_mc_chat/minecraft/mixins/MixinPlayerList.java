package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports player joins and leaves, plus the chat commands that produce a chat message.
 * <p>
 * {@code /say} and {@code /me} both end in
 * {@link PlayerList#broadcastChatMessage(PlayerChatMessage, CommandSourceStack, ChatType.Bound)}, which is a
 * real method on both loaders. Hooking it instead of the synthetic lambdas inside {@code SayCommand} and
 * {@code EmoteCommands} matters because a loader that patches those two classes would change the shape of
 * their lambdas, and an injection into a misshapen lambda aborts server startup.
 * <p>
 * Nothing else can reach this hook. Only four classes reference {@code broadcastChatMessage} anywhere in
 * 26.2: {@code SayCommand}, {@code EmoteCommands} and the two selectors below all use this exact overload,
 * and player chat goes to the sibling overload that takes a {@code ServerPlayer} instead of a
 * {@code CommandSourceStack}, so this injection is not on that path at all. The remaining two guards are
 * what the lambda entry points used to do themselves: non-player sources (console, command blocks) are
 * ignored, which is also why they are never cancelled, and the chat type separates the two commands.
 *
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
final class MixinPlayerList {

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	private void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
		MinecraftEventHandler.onPlayerJoin(player);
	}

	@Inject(method = "remove", at = @At("RETURN"))
	private void remove(ServerPlayer player, CallbackInfo ci) {
		MinecraftEventHandler.onPlayerQuit(player);
	}

	@Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V",
			at = @At("HEAD"), cancellable = true)
	private void dmcc$chatCommand(PlayerChatMessage message, CommandSourceStack sender, ChatType.Bound bound,
								  CallbackInfo ci) {
		// Console and command blocks have no ServerPlayer entity; they were never reported, and are not
		// cancelled either, so their output still reaches everyone normally.
		if (!(sender.getEntity() instanceof ServerPlayer)) {
			return;
		}

		if (bound.chatType().is(ChatType.SAY_COMMAND)) {
			MinecraftEventHandler.onSourceSay(sender, message);
		} else if (bound.chatType().is(ChatType.EMOTE_COMMAND)) {
			MinecraftEventHandler.onSourceMe(sender, message);
		} else {
			return;
		}

		if (Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.get()) {
			ci.cancel();
		}
	}
}
