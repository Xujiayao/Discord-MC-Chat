package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Links command implementation that displays all currently linked accounts.
 * <p>
 * Available in single_server and standalone modes (where Server is running).
 * Display names are resolved at query time; if resolution fails, raw IDs are shown.
 *
 * @author Xujiayao
 */
public class LinksCommand implements Command {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	@Override
	public String name() {
		return "links";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.links.description");
	}

	@Override
	public boolean isVisibleFromMinecraft() {
		return false;
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		Map<String, List<LinkedAccountManager.LinkEntry>> allLinks = LinkedAccountManager.getAllLinks();

		if (allLinks.isEmpty()) {
			sender.reply(I18nManager.getDmccTranslation("commands.links.no_links"));
			return;
		}

		StringBuilder builder = new StringBuilder();
		builder.append("========== ")
				.append(I18nManager.getDmccTranslation("commands.links.title"))
				.append(" ==========\n");

		int totalLinks = 0;
		for (Map.Entry<String, List<LinkedAccountManager.LinkEntry>> entry : allLinks.entrySet()) {
			String discordId = entry.getKey();
			List<LinkedAccountManager.LinkEntry> links = entry.getValue();
			totalLinks += links.size();

			String discordName = DiscordManager.resolveDiscordUserName(discordId);
			builder.append("\n[Discord: ").append(discordName).append(" (").append(discordId).append(")]");

			for (LinkedAccountManager.LinkEntry link : links) {
				String time = DATE_FORMATTER.format(Instant.ofEpochMilli(link.linkedAt()));
				String mcName = MojangUtils.resolvePlayerName(link.minecraftUuid());
				builder.append("\n  - MC: ").append(mcName).append(" (").append(link.minecraftUuid()).append(")");
				builder.append(" (").append(time).append(")");
			}
		}

		builder.append("\n\n")
				.append(I18nManager.getDmccTranslation("commands.links.total", totalLinks, allLinks.size()));

		sender.reply(builder.toString());
	}
}
