package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

/**
 * Info command implementation.
 *
 * @author Xujiayao
 */
public class InfoCommand implements Command {

	@Override
	public String name() {
		return "info";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.info.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		String message = switch (ModeManager.getMode()) {
			case "standalone" -> {
				// common_part + discord_part + server_part
				yield "";
			}
			case "single_server" -> {
				// common_part + discord_part + client_part
				yield "";
			}
			case "multi_server_client" -> {
				// common_part + client_part
				yield "";
			}
			default -> throw new IllegalStateException("Unexpected value: " + ModeManager.getMode());
		};
		sender.reply(message);
	}
}
