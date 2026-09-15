package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.server.message.MentionResolver;
import com.xujiayao.discord_mc_chat.server.message.MessageExtras;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.entities.sticker.StickerItem;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The only bridge between JDA messages and {@link DiscordMessageParser}.
 * <p>
 * Everything the parser needs is extracted here into plain values, which keeps the parsing pipeline free
 * of Discord API types and unit-testable with plain strings.
 *
 * @author Xujiayao
 */
public final class DiscordMessageAdapter {

	private DiscordMessageAdapter() {
	}

	/**
	 * @param member The Discord member (may be null).
	 * @return The hex color string for the member's highest colored role, or "white" when there is none.
	 */
	public static String roleColorHex(Member member) {
		if (member == null) {
			return "white";
		}
		Color color = member.getColors().getPrimary();
		if (color == null) {
			return "white";
		}
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/**
	 * @param message The Discord message.
	 * @return The author's display name, falling back to the account name outside a guild.
	 */
	public static String effectiveName(Message message) {
		Member member = message.getMember();
		return member != null ? member.getEffectiveName() : message.getAuthor().getName();
	}

	/**
	 * Builds the main chat line for a Discord message.
	 *
	 * @param message The Discord message.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> chatSegments(Message message) {
		return DiscordMessageParser.buildChatSegments(effectiveName(message), roleColorHex(message.getMember()),
				message.getContentRaw(), mentions(message), extras(message), DiscordMessageParser.Flags.fromConfig());
	}

	/**
	 * Builds the reply context line for the message a Discord message replies to.
	 *
	 * @param referenced The referenced message, or null when this is not a reply.
	 * @return The list of text segments, or null when there is no reply.
	 */
	public static List<TextSegment> replySegments(Message referenced) {
		if (referenced == null) {
			return null;
		}
		return DiscordMessageParser.buildReplySegments(effectiveName(referenced), roleColorHex(referenced.getMember()),
				referenced.getContentRaw(), mentions(referenced), extras(referenced),
				DiscordMessageParser.Flags.fromConfig());
	}

	/**
	 * Builds reply context segments from cached fields.
	 *
	 * @param refName        Referenced message author's display name.
	 * @param refRoleColor   Referenced message author's role color.
	 * @param contextMessage Message context for full parsing; null for cached or deleted messages.
	 * @param refRaw         Raw referenced message content.
	 * @return The list of text segments, or null when {@code refRaw} is null.
	 */
	public static List<TextSegment> replySegments(String refName, String refRoleColor, Message contextMessage, String refRaw) {
		if (refRaw == null) {
			return null;
		}
		DiscordMessageParser.Flags flags = DiscordMessageParser.Flags.fromConfig();
		if (contextMessage != null) {
			return DiscordMessageParser.buildReplySegments(refName, refRoleColor, refRaw, mentions(contextMessage),
					extras(contextMessage), flags);
		}
		return DiscordMessageParser.buildDetachedReplySegments(refName, refRoleColor, refRaw, flags);
	}

	/**
	 * Builds the content line shown after an edit notification.
	 *
	 * @param message The edited Discord message.
	 * @return The list of text segments.
	 */
	public static List<TextSegment> editedMessageSegments(Message message) {
		return DiscordMessageParser.buildEditedMessageSegments(effectiveName(message), roleColorHex(message.getMember()),
				message.getContentRaw(), mentions(message), extras(message), DiscordMessageParser.Flags.fromConfig());
	}

	/**
	 * @param message The Discord message.
	 * @return Whether the message mentions {@code @everyone} or {@code @here}.
	 */
	public static boolean isMentionEveryone(Message message) {
		return message.getMentions().mentionsEveryone();
	}

	/**
	 * Collects the Minecraft player UUIDs that should be notified about mentions in this message.
	 * <p>
	 * Checks both user mentions (via account linking) and role mentions (via linked accounts that have the
	 * mentioned role).
	 *
	 * @param message The Discord message.
	 * @return A set of Minecraft player UUID strings to notify.
	 */
	public static Set<String> collectMentionedPlayerUuids(Message message) {
		Set<String> uuids = new HashSet<>();
		for (User mentionedUser : message.getMentions().getUsers()) {
			uuids.addAll(LinkedAccountManager.getMinecraftUuidsByDiscordId(mentionedUser.getId()));
		}
		for (Role mentionedRole : message.getMentions().getRoles()) {
			for (Member member : message.getGuild().getMembersWithRoles(mentionedRole)) {
				uuids.addAll(LinkedAccountManager.getMinecraftUuidsByDiscordId(member.getUser().getId()));
			}
		}
		// @everyone / @here is handled through the mentionEveryone flag, which notifies every online player
		// rather than only the linked ones.
		return uuids;
	}

	/**
	 * Builds a resolver that answers mention lookups from the mentions JDA already parsed for this message.
	 */
	private static MentionResolver mentions(Message message) {
		return new MentionResolver() {
			@Override
			public Mention user(String id) {
				for (User user : message.getMentions().getUsers()) {
					if (user.getId().equals(id)) {
						Member member = message.getGuild().getMember(user);
						return new Mention(member != null ? member.getEffectiveName() : user.getName(),
								roleColorHex(member));
					}
				}
				return null;
			}

			@Override
			public Mention role(String id) {
				for (Role role : message.getMentions().getRoles()) {
					if (role.getId().equals(id)) {
						Color color = role.getColors().getPrimary();
						return new Mention(role.getName(),
								color != null ? String.format("#%06X", color.getRGB() & 0xFFFFFF) : "white");
					}
				}
				return null;
			}

			@Override
			public String channel(String id) {
				for (GuildChannel channel : message.getMentions().getChannels()) {
					if (channel.getId().equals(id)) {
						return channel.getName();
					}
				}
				return null;
			}

			@Override
			public boolean mentionsEveryone() {
				return message.getMentions().mentionsEveryone();
			}
		};
	}

	/**
	 * Extracts the non-textual parts of a message.
	 */
	private static MessageExtras extras(Message message) {
		List<MessageExtras.Attachment> attachments = new ArrayList<>();
		for (Message.Attachment attachment : message.getAttachments()) {
			String type = "file";
			if (attachment.isImage()) {
				type = "image";
			} else if (attachment.isVideo()) {
				type = "video";
			}
			attachments.add(new MessageExtras.Attachment(type, attachment.getFileName(), attachment.getUrl(),
					attachment.isSpoiler() || attachment.getFileName().startsWith("SPOILER_")));
		}

		List<String> stickers = new ArrayList<>();
		for (StickerItem sticker : message.getStickers()) {
			stickers.add(sticker.getName());
		}

		List<MessageExtras.Embed> embeds = new ArrayList<>();
		for (MessageEmbed embed : message.getEmbeds()) {
			embeds.add(new MessageExtras.Embed(embed.getTitle(), embed.getDescription(), embed.getUrl()));
		}

		MessagePoll poll = message.getPoll();
		return new MessageExtras(attachments, stickers, embeds, !message.getComponents().isEmpty(),
				poll != null ? poll.getQuestion().getText() : null);
	}
}
