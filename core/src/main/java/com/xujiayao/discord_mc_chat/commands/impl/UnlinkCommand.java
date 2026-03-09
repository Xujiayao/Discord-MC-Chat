package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.linking.UnlinkByUuidRequestPacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

/**
 * Unlink command implementation supporting both MC-side and Discord-side workflows.
 * <p>
 * <b>Minecraft side:</b> Unlinks the executing player's Minecraft account.
 * <b>Discord side:</b> Unlinks all Minecraft accounts associated with the Discord user.
 *
 * @author Xujiayao
 */
public class UnlinkCommand implements Command {

	@Override
	public String name() {
		return "unlink";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.unlink.description");
	}

	@Override
	public boolean isVisibleInHelp(CommandSender sender) {
		return (sender instanceof LinkCommand.PlayerContextProvider) || (sender instanceof LinkCommand.DiscordUserContextProvider);
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if (sender instanceof LinkCommand.PlayerContextProvider player) {
			executeMcUnlink(sender, player);
		} else if (sender instanceof LinkCommand.DiscordUserContextProvider discord) {
			executeDiscordUnlink(sender, discord);
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.unlink.not_available"));
		}
	}

	/**
	 * Minecraft-side unlink: removes the executing player's link.
	 */
	private void executeMcUnlink(CommandSender sender, LinkCommand.PlayerContextProvider player) {
		String uuid = player.getPlayerUuid();
		String name = player.getPlayerName();

		switch (ModeManager.getMode()) {
			case "single_server" -> {
				boolean success = LinkedAccountManager.unlinkByMinecraftUuid(uuid);
				if (success) {
					sender.reply(I18nManager.getDmccTranslation("commands.unlink.success"));
				} else {
					sender.reply(I18nManager.getDmccTranslation("commands.unlink.not_linked"));
				}
			}
			case "multi_server_client" -> {
				NetworkManager.sendPacketToServer(new UnlinkByUuidRequestPacket(uuid, name));
				sender.reply(I18nManager.getDmccTranslation("commands.unlink.request_sent"));
			}
			default -> sender.reply(I18nManager.getDmccTranslation("commands.unlink.not_available"));
		}
	}

	/**
	 * Discord-side unlink: removes all links for the Discord user.
	 */
	private void executeDiscordUnlink(CommandSender sender, LinkCommand.DiscordUserContextProvider discord) {
		String discordId = discord.getDiscordUserId();
		int count = LinkedAccountManager.unlinkByDiscordId(discordId);

		if (count > 0) {
			sender.reply(I18nManager.getDmccTranslation("commands.unlink.discord_success", count));
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.unlink.not_linked"));
		}
	}
}
