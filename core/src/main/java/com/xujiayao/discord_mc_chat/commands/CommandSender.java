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

	/**
	 * Sends a message with a file attachment back to the command sender.
	 * <p>
	 * Default implementation falls back to reply with message only.
	 *
	 * @param message  The message to send.
	 * @param fileData The file content as bytes.
	 * @param fileName The file name.
	 */
	default void replyWithFile(String message, byte[] fileData, String fileName) {
		reply(message);
	}

	/**
	 * Gets the permission level of the sender.
	 * <p>
	 * Defaults to maximum privileges (OP 4) for local/console senders,
	 * ensuring unrestricted access for terminal and in-game operators.
	 * Remote senders (Discord) will override this with a resolved value.
	 *
	 * @return The OP level (-1 to 4).
	 */
	default int getOpLevel() {
		return 4;
	}
}
