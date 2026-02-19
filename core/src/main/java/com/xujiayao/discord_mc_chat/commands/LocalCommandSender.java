package com.xujiayao.discord_mc_chat.commands;

/**
 * Marker interface for command senders that represent local/direct interaction
 * (e.g., Minecraft in-game commands, standalone terminal).
 * <p>
 * Commands that only make sense for remote or Discord senders (like {@code log},
 * which requires file upload capability) can use this marker to hide themselves
 * from help listings for local senders.
 *
 * @author Xujiayao
 */
public interface LocalCommandSender extends CommandSender {
}
