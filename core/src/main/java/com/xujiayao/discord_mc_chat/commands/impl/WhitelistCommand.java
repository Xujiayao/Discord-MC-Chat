package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.events.CoreEvents;
import com.xujiayao.discord_mc_chat.events.EventManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Proxy command for managing the server whitelist via DMCC.
 * <p>
 * This DMCC command delegates to the Minecraft native {@code /whitelist add <player>} command
 * using a virtual elevated sender (OP 4). Because the DMCC-level permission check
 * (configured in {@code command_permission_levels.whitelist}) has already been performed
 * by CommandManager before this method is called, it is safe to elevate the native execution.
 * <p>
 * This design solves the "Whitelist Catch-22" problem: players who can't join the server
 * (because they aren't whitelisted) can have a trusted Discord user add them via this command,
 * even though the native {@code /whitelist} command requires OP 3.
 * <p>
 * Because we delegate to Minecraft's native whitelist command, this works correctly on both
 * online-mode and offline-mode servers — Minecraft handles the name-to-UUID resolution internally.
 *
 * @author Xujiayao
 */
public final class WhitelistCommand implements Command {

	private static final int WHITELIST_COMMAND_TIMEOUT_SECONDS = 10;

	/**
	 * Creates a whitelist command instance.
	 */
	public WhitelistCommand() {
	}

	@Override
	public String name() {
		return "whitelist";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[]{
				new CommandArgument() {
					@Override
					public String name() {
						return "player";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.whitelist.args_desc.player");
					}
				}
		};
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.whitelist.description");
	}

	@Override
	public boolean isVisibleFromMinecraft() {
		return false;
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		String player = args[0];

		// We elevate the sender to OP 4 temporarily for the Minecraft backend dispatch.
		// The DMCC-level permission check has already been done by CommandManager.executeInternal()
		// before reaching this point, so it is safe to bypass Minecraft's native OP restriction.
		CommandSender elevatedSender = new CommandSender() {
			@Override
			public void reply(String message) {
				sender.reply(message);
			}

			@Override
			public void replyWithFile(String message, byte[] fileData, String fileName) {
				sender.replyWithFile(message, fileData, fileName);
			}

			@Override
			public int getOpLevel() {
				return 4; // Max permission to bypass native /whitelist OP 3 restriction
			}
		};

		// Delegate to Minecraft's native /whitelist add command with callback-based completion
		CompletableFuture<Void> completionFuture = new CompletableFuture<>();

		EventManager.post(new CoreEvents.MinecraftCommandExecutionEvent(elevatedSender, "whitelist add " + player, completionFuture));

		// Wait for the command to complete so the response is available before this method returns.
		// This is critical for remote execution (execute command) where the response is collected
		// from the sender's reply buffer after this method returns.
		try {
			completionFuture.get(WHITELIST_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			// Timeout or interruption - output produced so far will still be in the sender's buffer.
		}
	}
}
