package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.commands.link.LinkRequestPacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.server.linking.VerificationCodeManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

/**
 * Link command implementation supporting both MC-side and Discord-side workflows.
 * <p>
 * <b>Minecraft side (0 args):</b> Generates or refreshes a verification code for the executing player.
 * Available in single_server and multi_server_client modes.
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
					new CommandArgument() {
						@Override
						public String name() {
							return "code";
						}

						@Override
						public String description() {
							return I18nManager.getDmccTranslation("commands.link.args_desc.code");
						}
					}
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
			// Minecraft-side: generate/refresh verification code
			executeMcLink(sender);
		} else if (args.length == 1) {
			// Discord-side: complete linking with verification code
			executeDiscordLink(sender, args[0]);
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.invalid_usage", "link <code>"));
		}
	}

	/**
	 * Minecraft-side link: generates or refreshes a verification code.
	 */
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
					NetworkManager.sendPacketToServer(new LinkRequestPacket(uuid, name, false));
			default -> sender.reply(I18nManager.getDmccTranslation("commands.link.not_available"));
		}
	}

	/**
	 * Discord-side link: validates the code and creates the account link.
	 */
	private void executeDiscordLink(CommandSender sender, String code) {
		if (!(sender instanceof DiscordUserContextProvider discord)) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.discord_only"));
			return;
		}

		String discordId = discord.getDiscordUserId();
		String discordName = discord.getDiscordUserName();

		VerificationCodeManager.PendingVerification pending = VerificationCodeManager.consumeCode(code);
		if (pending == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.invalid_code"));
			return;
		}

		boolean success = LinkedAccountManager.linkAccount(discordId, discordName, pending.minecraftUuid(), pending.playerName());
		if (success) {
			sender.reply(I18nManager.getDmccTranslation("commands.link.success", pending.playerName()));
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.link.uuid_already_linked"));
		}
	}

	/**
	 * Functional interface for providing player context in Minecraft-side link commands.
	 * <p>
	 * Implementations provide the player's UUID and name from the Minecraft server.
	 */
	public interface PlayerContextProvider {
		/**
		 * Gets the player's UUID as a string.
		 *
		 * @return The player UUID string.
		 */
		String getPlayerUuid();

		/**
		 * Gets the player's display name.
		 *
		 * @return The player name.
		 */
		String getPlayerName();
	}

	/**
	 * Functional interface for providing Discord user context in Discord-side link commands.
	 * <p>
	 * Implementations provide the Discord user's ID.
	 */
	public interface DiscordUserContextProvider {
		/**
		 * Gets the Discord user's ID.
		 *
		 * @return The Discord user ID.
		 */
		String getDiscordUserId();

		/**
		 * Gets the Discord user's display name.
		 *
		 * @return The Discord username.
		 */
		String getDiscordUserName();
	}
}
