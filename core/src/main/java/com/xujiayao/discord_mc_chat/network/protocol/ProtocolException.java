package com.xujiayao.discord_mc_chat.network.protocol;

/**
 * Thrown when a received frame cannot be understood as a DMCC packet.
 *
 * @author Xujiayao
 */
public class ProtocolException extends RuntimeException {

	/**
	 * Creates a protocol exception.
	 *
	 * @param message Description of the problem.
	 */
	public ProtocolException(String message) {
		super(message);
	}

	/**
	 * Creates a protocol exception with a cause.
	 *
	 * @param message Description of the problem.
	 * @param cause   Underlying failure.
	 */
	public ProtocolException(String message, Throwable cause) {
		super(message, cause);
	}
}
