package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import com.xujiayao.discord_mc_chat.utils.logging.impl.LoggerImpl;

import static com.xujiayao.discord_mc_chat.standalone.StandaloneDMCC.SHUTDOWN_THREAD;

/**
 * Shutdown command implementation (standalone only).
 *
 * @author Xujiayao
 */
public final class ShutdownCommand implements Command {

	/**
	 * Creates a shutdown command instance.
	 */
	public ShutdownCommand() {
	}

	@Override
	public String name() {
		return "shutdown";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[0];
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.shutdown.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		sender.reply(I18nManager.getDmccTranslation("commands.shutdown.shutting_down"));

		if (DMCC.shutdown()) {
			sender.reply(I18nManager.getDmccTranslation("commands.shutdown.success"));

			Runtime.getRuntime().removeShutdownHook(SHUTDOWN_THREAD);

			// Logger cleanup
			LoggerImpl.shutdown();

			System.exit(0);
		} else {
			sender.reply(I18nManager.getDmccTranslation("commands.shutdown.failure"));
		}
	}
}
