package com.xujiayao.discord_mc_chat.commands;

/**
 * Represents an entity that can send commands to DMCC.
 *
 * @author Xujiayao
 */
public interface CommandSender {

	/**
	 * Sends a message back to the command sender.
	 * <p>
	 * One line per call.
	 *
	 * @param message The message to send.
	 */
	void reply(String message);
}
