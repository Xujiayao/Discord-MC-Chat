package com.xujiayao.discord_mc_chat.server.message;

/**
 * Resolves Discord mention ids to display information.
 * <p>
 * The whole {@code discord_to_minecraft} parsing pipeline works against this interface instead of JDA
 * types, which keeps the parser free of Discord API dependencies and unit-testable with plain strings.
 * The production implementation is backed by the JDA message being parsed; tests can supply a stub.
 *
 * @author Xujiayao
 */
public interface MentionResolver {

	/**
	 * Resolves a user mention id.
	 *
	 * @param id Discord user id.
	 * @return The resolved mention, or null when the id is not a known user mention of this message.
	 */
	Mention user(String id);

	/**
	 * Resolves a role mention id.
	 *
	 * @param id Discord role id.
	 * @return The resolved mention, or null when the id is not a known role mention of this message.
	 */
	Mention role(String id);

	/**
	 * Resolves a channel mention id.
	 *
	 * @param id Discord channel id.
	 * @return The channel name, or null when the id is not a known channel mention of this message.
	 */
	String channel(String id);

	/**
	 * @return Whether the message contains an {@code @everyone} or {@code @here} mention.
	 */
	boolean mentionsEveryone();

	/**
	 * A resolved mention target.
	 *
	 * @param name  Display name shown inside the {@code [@name]} token.
	 * @param color The role color as a hex string (e.g. {@code #FF0000}) or a Minecraft color name.
	 */
	record Mention(String name, String color) {
	}
}
