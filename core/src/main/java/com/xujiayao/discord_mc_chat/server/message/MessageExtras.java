package com.xujiayao.discord_mc_chat.server.message;

import java.util.List;

/**
 * The non-textual parts of a Discord message that the parser renders as inline labels.
 * <p>
 * Keeping them in a plain record lets the parsing pipeline stay free of JDA types. A JDA-backed adapter
 * extracts these values from a live {@code Message}.
 *
 * @param attachments   File attachments in the order Discord reports them.
 * @param stickers      Sticker names.
 * @param embeds        Rich embeds.
 * @param hasComponents Whether the message carries interactive components (buttons, select menus, ...).
 * @param pollQuestion  The poll question, or null when the message has no poll.
 * @author Xujiayao
 */
public record MessageExtras(
		List<Attachment> attachments,
		List<String> stickers,
		List<Embed> embeds,
		boolean hasComponents,
		String pollQuestion
) {

	/**
	 * @param type     One of {@code file}, {@code image} or {@code video}.
	 * @param fileName Display file name.
	 * @param url      Download URL.
	 * @param spoiler  Whether Discord marked the attachment as a spoiler.
	 */
	public record Attachment(String type, String fileName, String url, boolean spoiler) {
	}

	/**
	 * @param title       Embed title, or null.
	 * @param description Embed description, or null.
	 * @param url         Embed URL, or null.
	 */
	public record Embed(String title, String description, String url) {
	}
}
