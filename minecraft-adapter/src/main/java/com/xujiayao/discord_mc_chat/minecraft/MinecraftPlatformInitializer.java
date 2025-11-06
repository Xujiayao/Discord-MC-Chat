package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.interfaces.IPlatformInitializer;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;

/**
 * The Minecraft-specific implementation of the platform initializer service.
 *
 * @author Xujiayao
 */
public class MinecraftPlatformInitializer implements IPlatformInitializer {

	@Override
	public void initialize() {
		// This is where the platform-specific code is called.
		MinecraftEventHandler.init();
	}
}
