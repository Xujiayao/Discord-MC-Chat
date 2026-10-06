package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets.Link.RequestPacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.utils.MojangUtils;

import java.util.List;

/**
 * Link command implementation supporting both MC-side and Discord-side workflows.
 * <p>
 * <b>Minecraft side (0 args):</b> Generates or refreshes a verification code for the executing player.
 * Available in single_server and multi_server_client modes.
 * <p>
 * <b>Discord side (0 args):</b> Reports the Minecraft players currently linked to the executing Discord user.
 * <p>
 * <b>Discord side (1 arg):</b> Completes account linking using a verification code.
 * Available in single_server and standalone modes (where Server is running).
 *
 * @author Xujiayao
 */
public final class LinkCommand implements Command {

	@Override
	public String name() {
		return "link";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public CommandArgument[] argsForSender(CommandSender sender) {
		// In Discord context, show the <code> argument
		if (sender instanceof DiscordUserContextProvider) {
			return new CommandArgument[]{
					new CommandArgument("code", I18nManager.getDmccTranslation("commands.link.args_desc.code"))
			};
		}
		// In Minecraft context, show no arguments
		return new CommandArgument[0];
	}

	@Override
	public boolean acceptsExtraArgs() {
		return true;
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.link.description");
	}

	@Override
	public boolean isAutoCompletable() {
		return false;
	}

	@Override
	public boolean isVisibleInHelp(CommandSender sender) {
		// Only show link in help for Minecraft players or Discord users
		return (sender instanceof PlayerContextProvider) || (sender instanceof DiscordUserContextProvider);
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if (args.length == 0) {
			if (sender instanceof DiscordUserContextProvider discord) {
				// Discord-side without a code: report the current linking status
				executeDiscordStatus(sender, discord);
				return;
			}
			// Minecraft-side: generate/refresh verification code
			executeMcLink(sender);
		} else if (args.length == 1) {
			// Discord-side: complete linking with verification code
			executeDiscordLink(sender, args[0]);
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.invalid_usage", "link <code>"));
		}
	}

	private void executeMcLink(CommandSender sender) {
		if (!(sender instanceof PlayerContextProvider player)) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.player_only"));
			return;
		}

		String uuid = player.getPlayerUuid();
		String name = player.getPlayerName();

		if (uuid == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.player_only"));
			return;
		}

		switch (ModeManager.getMode()) {
			case "single_server", "multi_server_client" -> // Send request to server via network (same for both modes)
					NetworkManager.sendPacketToServer(new RequestPacket(uuid, name, false));
			default -> sender.reply(I18nManager.getDmccTranslation("commands.link.not_available"));
		}
	}

	private void executeDiscordLink(CommandSender sender, String code) {
		if (!(sender instanceof DiscordUserContextProvider discord)) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.discord_only"));
			return;
		}

		// The code option is optional on Discord, so a null code means "report the status"
		if (code == null) {
			executeDiscordStatus(sender, discord);
			return;
		}

		String discordId = discord.getDiscordUserId();
		String discordName = discord.getDiscordUserName();

		VerificationCodeManager.PendingVerification pending = VerificationCodeManager.consumeCode(code);
		if (pending == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.invalid_code"));
			return;
		}

		LinkedAccountManager.LinkResult result = LinkedAccountManager.linkAccount(discordId, discordName, pending.minecraftUuid(), pending.playerName());
		switch (result) {
			case SUCCESS -> sender.reply(I18nManager.getDmccTranslation("commands.link.success", pending.playerName()));
			case UUID_ALREADY_LINKED -> sender.reply(I18nManager.getDmccTranslation("commands.link.uuid_already_linked"));
			case LIMIT_REACHED -> sender.reply(I18nManager.getDmccTranslation("commands.link.limit_reached",
					Math.max(0, ConfigManager.getInt("account_linking.max_minecraft_accounts_per_discord_user", 1)),
					describeLinkedPlayers(LinkedAccountManager.getLinkEntriesByDiscordId(discordId))));
		}
	}

	/**
	 * Reports the Minecraft players linked to a Discord user, without consuming any verification code.
	 */
	private void executeDiscordStatus(CommandSender sender, DiscordUserContextProvider discord) {
		List<LinkedAccountManager.LinkEntry> entries = LinkedAccountManager.getLinkEntriesByDiscordId(discord.getDiscordUserId());

		if (entries.isEmpty()) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.status_not_linked"));
			return;
		}

		sender.reply(I18nManager.getDmccTranslation("commands.link.status_linked", entries.size(), describeLinkedPlayers(entries)));
	}

	/**
	 * Renders linked accounts as a comma-separated list of Minecraft player names.
	 */
	private static String describeLinkedPlayers(List<LinkedAccountManager.LinkEntry> entries) {
		StringBuilder players = new StringBuilder();
		for (LinkedAccountManager.LinkEntry entry : entries) {
			if (!players.isEmpty()) {
				players.append(", ");
			}
			players.append(MojangUtils.resolvePlayerName(entry.minecraftUuid(), entry.offlinePlayerName()));
		}
		return players.toString();
	}

	/**
	 * Functional interface for providing player context in Minecraft-side link commands.
	 * <p>
	 * Implementations provide the player's UUID and name from the Minecraft server.
	 */
	public interface PlayerContextProvider {
		String getPlayerUuid();

		String getPlayerName();
	}

	/**
	 * Functional interface for providing Discord user context in Discord-side link commands.
	 * <p>
	 * Implementations provide the Discord user's ID.
	 */
	public interface DiscordUserContextProvider {
		String getDiscordUserId();

		String getDiscordUserName();
	}
}
