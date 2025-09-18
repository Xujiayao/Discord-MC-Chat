package com.xujiayao.discord_mc_chat.common.commands;

/**
 * A container for all command-related event classes.
 *
 * @author Xujiayao
 */
public class CommandEvents {

	/**
	 * Posted when a reload is requested.
	 */
	public record ReloadEvent() {
	}

	/**
	 * Posted when a stop is requested.
	 */
	public record StopEvent() {
	}
}