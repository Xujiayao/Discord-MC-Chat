package com.xujiayao.discord_mc_chat.common.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftIntegration;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * 玩家事件Mixin
 * 负责捕获玩家加入和离开事件
 * 
 * @author Xujiayao
 */
@Mixin(PlayerList.class)
public class MixinPlayerEvents {
    
    /**
     * 捕获玩家加入事件
     */
    @Inject(at = @At("TAIL"), method = "placeNewPlayer")
    private void onPlayerJoin(Connection connection, ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getName().getString();
                LOGGER.debug("捕获玩家加入: {}", playerName);
                MinecraftIntegration.handlePlayerJoin(playerName);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家加入事件Mixin时发生错误", e);
        }
    }
    
    /**
     * 捕获玩家离开事件
     */
    @Inject(at = @At("HEAD"), method = "remove")
    private void onPlayerLeave(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getName().getString();
                LOGGER.debug("捕获玩家离开: {}", playerName);
                MinecraftIntegration.handlePlayerLeave(playerName);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家离开事件Mixin时发生错误", e);
        }
    }
}