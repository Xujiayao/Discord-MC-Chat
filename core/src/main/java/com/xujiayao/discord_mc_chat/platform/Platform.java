package com.xujiayao.discord_mc_chat.platform;

/**
 * Holds the {@link PlatformHost} registered by the running platform.
 * <p>
 * Until {@link #set(PlatformHost)} is called (and in the standalone environment, which has no
 * Minecraft platform at all) the holder answers with {@link NoopPlatformHost}, so core code can
 * call it unconditionally without null checks.
 *
 * @author Xujiayao
 */
public final class Platform {

	private static volatile PlatformHost host = NoopPlatformHost.INSTANCE;

	private Platform() {
	}

	/**
	 * Registers the platform implementation. Called once during DMCC initialization.
	 */
	public static void set(PlatformHost platformHost) {
		host = platformHost == null ? NoopPlatformHost.INSTANCE : platformHost;
	}

	/**
	 * Gets the registered platform implementation, never {@code null}.
	 *
	 * @return The active platform implementation.
	 */
	public static PlatformHost host() {
		return host;
	}
}
