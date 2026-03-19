package com.xujiayao.discord_mc_chat.client;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime policies pushed from DMCC server to DMCC clients after login.
 *
 * @author Xujiayao
 */
public final class ClientRuntimePolicy {

	private static final AtomicBoolean CANCEL_LOCAL_SOURCE_MESSAGES = new AtomicBoolean(false);

	private ClientRuntimePolicy() {
	}

	public static boolean shouldCancelLocalSourceMessages() {
		return CANCEL_LOCAL_SOURCE_MESSAGES.get();
	}

	public static void setCancelLocalSourceMessages(boolean value) {
		CANCEL_LOCAL_SOURCE_MESSAGES.set(value);
	}
}
