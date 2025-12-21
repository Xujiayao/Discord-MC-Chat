package com.xujiayao.discord_mc_chat.commands;

/**
 * A container for all command-related event classes.
 *
 * @author Xujiayao
 */
public class CommandEvents {

	/**
	 * Posted when someone requests to reload DMCC.
	 */
	public record ReloadEvent() {
	}

	/**
	 * Posted when someone requests to shut down DMCC.
	 */
	public record ShutdownEvent() {
	}
}
