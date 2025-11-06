package com.xujiayao.discord_mc_chat.interfaces;

/**
 * A service provider interface for platform-specific initializers.
 * This allows the common module to trigger initialization in platform-specific modules
 * like minecraft-adapter without depending on them directly.
 *
 * @author Xujiayao
 */
public interface IPlatformInitializer {

	/**
	 * Performs platform-specific initialization.
	 */
	void initialize();
}
