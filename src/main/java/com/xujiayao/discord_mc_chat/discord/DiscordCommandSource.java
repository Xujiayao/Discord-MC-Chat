package com.xujiayao.discord_mc_chat.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

//#if MC < 11900
//$$ import java.util.UUID;
//#endif
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Xujiayao
 */
public class DiscordCommandSource implements CommandSource {

	private final SlashCommandInteractionEvent e;
	private StringBuilder output = new StringBuilder("```\n");
	private long lastOutputMillis = 0;

	public DiscordCommandSource(SlashCommandInteractionEvent e) {
		this.e = e;
	}

	@Override
	//#if MC >= 11900
	public void sendSystemMessage(Component message) {
	//#elseif MC > 11502
	//$$ public void sendMessage(Component message, UUID uUID) {
	//#else
	//$$ public void sendMessage(Component message) {
	//#endif
		long currentOutputMillis = System.currentTimeMillis();

		if (output.length() > 1500) {
			output.append("```");
			e.getChannel().sendMessage(output.toString()).queue();
			output = new StringBuilder("```\n");
		} else {
			output.append(message.getString()).append("\n");
		}

		if ((currentOutputMillis - lastOutputMillis) > 50) {
			new Thread(() -> new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					output.append("```");
					e.getChannel().sendMessage(output.toString()).queue();
					output = new StringBuilder("```\n");
				}
			}, 51)).start();
		}

		lastOutputMillis = currentOutputMillis;
	}

	@Override
	public boolean acceptsSuccess() {
		return true;
	}

	@Override
	public boolean acceptsFailure() {
		return true;
	}

	@Override
	public boolean shouldInformAdmins() {
		return true;
	}
}
