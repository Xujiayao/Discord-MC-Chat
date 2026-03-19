package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by server to confirm authentication success.
 *
 * @author Xujiayao
 */
public class LoginSuccessPacket extends Packet {
	public String language;
	public boolean cancelLocalSourceMessages;

	public LoginSuccessPacket(String language, boolean cancelLocalSourceMessages) {
		this.language = language;
		this.cancelLocalSourceMessages = cancelLocalSourceMessages;
	}
}
