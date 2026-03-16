package com.xujiayao.discord_mc_chat.network.packets.events;

import com.xujiayao.discord_mc_chat.network.packets.Packet;

import java.util.List;

/**
 * Packet sent from DMCC Server to Client(s) when a Discord event needs to be
 * rendered in Minecraft.
 * <p>
 * The server pre-processes the Discord message into a list of {@link TextSegment}
 * objects so that the client can directly build Minecraft Components without
 * needing access to Discord APIs or custom_messages resources.
 *
 * @author Xujiayao
 */
public class DiscordEventPacket extends Packet {

	/** The type of Discord event. */
	public EventType type;

	/** Pre-built rich text segments for the main message line. */
	public List<TextSegment> segments;

	/**
	 * Pre-built rich text segments for the reply/response line (the ┌──── line).
	 * <p>
	 * Only populated when the Discord message is a reply to another message.
	 * Null or empty when there is no reply context.
	 */
	public List<TextSegment> replySegments;

	/**
	 * Pre-built rich text for the mention notification (e.g. "Xujiayao mentioned you!").
	 * <p>
	 * Only populated when a Minecraft player linked to a mentioned Discord user is online.
	 * Null when no mention notification is needed.
	 */
	public String mentionNotificationText;

	/**
	 * The notification style for mentions: "action_bar", "title", or "chat".
	 * Only meaningful when {@link #mentionNotificationText} is non-null.
	 */
	public String mentionNotificationStyle;

	/**
	 * A list of Minecraft player UUIDs (as strings) who should receive the mention notification.
	 * Only meaningful when {@link #mentionNotificationText} is non-null.
	 */
	public List<String> mentionedPlayerUuids;

	/**
	 * Creates a DiscordEventPacket for a chat message.
	 *
	 * @param type     The event type.
	 * @param segments The pre-built text segments for the main message line.
	 */
	public DiscordEventPacket(EventType type, List<TextSegment> segments) {
		this.type = type;
		this.segments = segments;
	}

	/**
	 * Enum representing the type of Discord event being forwarded.
	 *
	 * @author Xujiayao
	 */
	public enum EventType {
		/** A regular Discord chat message forwarded to Minecraft. */
		CHAT,

		/** A Discord command execution notification forwarded to Minecraft. */
		COMMAND
	}
}
