package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.message.TextSegment;

import java.util.List;

/**
 * Packet sent from DMCC Server to Minecraft client(s) when a message originating
 * from Minecraft needs to be rendered in chat.
 *
 * @author Xujiayao
 */
public final class MinecraftRelayPacket extends Packet {

	public MessageType type;
	public List<TextSegment> segments;
	public String componentJson;
	public String componentPlaceholder;
	public String componentText;
	public String mentionNotificationText;
	public String mentionNotificationStyle;
	public List<String> mentionedPlayerUuids;
	public boolean mentionEveryone;

	public MinecraftRelayPacket(MessageType type, List<TextSegment> segments) {
		this.type = type;
		this.segments = segments;
	}

	public enum MessageType {
		USER_MESSAGE,
		SYSTEM_MESSAGE,
		COMMAND
	}
}

