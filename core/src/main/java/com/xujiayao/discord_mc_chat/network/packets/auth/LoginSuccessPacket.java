package com.xujiayao.discord_mc_chat.network.packets.auth;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

/**
 * Sent by server to confirm authentication success.
 *
 * @author Xujiayao
 */
public final class LoginSuccessPacket extends Packet {
	public String language;
	public boolean overwriteMinecraftSourceMessages;

	public LoginSuccessPacket(String language, boolean overwriteMinecraftSourceMessages) {
		this.language = language;
		this.overwriteMinecraftSourceMessages = overwriteMinecraftSourceMessages;
	}
}
