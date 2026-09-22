package com.xujiayao.discord_mc_chat.network.packets;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Xujiayao
 */
public abstract class Packet implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	protected Packet() {
	}
}
