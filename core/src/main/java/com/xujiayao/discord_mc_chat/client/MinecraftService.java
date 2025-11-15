package com.xujiayao.discord_mc_chat.client;

/**
 * A service interface to be implemented by the Minecraft adapter.
 * This allows the common module to initialize Minecraft-specific components
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
