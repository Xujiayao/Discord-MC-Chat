package com.xujiayao.discord_mc_chat.common.mixins;

import com.xujiayao.discord_mc_chat.common.minecraft.MinecraftIntegration;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.xujiayao.discord_mc_chat.common.DMCC.LOGGER;

/**
 * Minecraft服务器Mixin
 * 负责在服务器生命周期事件中集成DMCC功能
 * 
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
	
	/**
	 * 服务器启动时初始化DMCC Minecraft集成
	 */
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void onServerStarting(CallbackInfo info) {
		try {
			LOGGER.info("服务器正在启动，初始化DMCC Minecraft集成...");
			MinecraftIntegration.getInstance().initialize();
		} catch (Exception e) {
			LOGGER.error("初始化DMCC Minecraft集成时发生错误", e);
		}
	}
	
	/**
	 * 服务器关闭时清理DMCC Minecraft集成
	 */
	@Inject(at = @At("HEAD"), method = "stopServer")
	private void onServerStopping(CallbackInfo info) {
		try {
			LOGGER.info("服务器正在关闭，清理DMCC Minecraft集成...");
			MinecraftIntegration.getInstance().shutdown();
		} catch (Exception e) {
			LOGGER.error("清理DMCC Minecraft集成时发生错误", e);
		}
	}
}
