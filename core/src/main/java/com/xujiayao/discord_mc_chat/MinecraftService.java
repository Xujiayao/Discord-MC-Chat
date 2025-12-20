package com.xujiayao.discord_mc_chat;

/**
 * A service interface to be implemented by the Minecraft adapter.
 * This allows the core module to initialize Minecraft-specific components
 * without having a direct dependency on them.
 *
 * @author Xujiayao
 */
public interface MinecraftService {

	/**
	 * Initializes Minecraft-specific components, such as event handlers.
	 */
	void initialize();
}
