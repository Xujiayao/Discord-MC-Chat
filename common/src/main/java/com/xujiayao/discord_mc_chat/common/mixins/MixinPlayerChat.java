package com.xujiayao.discord_mc_chat.common.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftIntegration;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * 玩家聊天Mixin
 * 负责捕获玩家聊天消息并转发到Discord
 * 
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
public class MixinPlayerChat {
    
    /**
     * 捕获玩家聊天消息
     */
    @Inject(at = @At("HEAD"), method = "broadcastChatMessage")
    private void onPlayerChat(Component message, ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && message != null) {
                String playerName = player.getName().getString();
                String messageText = message.getString();
                
                LOGGER.debug("捕获玩家聊天: {} -> {}", playerName, messageText);
                MinecraftIntegration.handlePlayerChat(playerName, messageText);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家聊天消息Mixin时发生错误", e);
        }
    }
}