package top.xujiayao.mcdiscordchat.discord;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;

/**
 * @author Xujiayao
 */
public class DiscordCommandOutput implements CommandOutput {

	private StringBuilder output = new StringBuilder("```\n");
	private long lastOutputMillis = 0;

	@Override
	public void sendSystemMessage(Text message, UUID sender) {
		long currentOutputMillis = System.currentTimeMillis();

		if (output.length() > 1500) {
			output.append("```");
			CHANNEL.sendMessage(output.toString()).queue();
			output = new StringBuilder("```\n");
		} else {
			output.append(message.getString()).append("\n");
		}

		if ((currentOutputMillis - lastOutputMillis) > 50) {
			new Thread(() -> new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					output.append("```");
					CHANNEL.sendMessage(output.toString()).queue();
					output = new StringBuilder("```\n");
				}
			}, 51)).start();
		}

		lastOutputMillis = currentOutputMillis;
	}

	@Override
	public boolean shouldReceiveFeedback() {
		return true;
	}

	@Override
	public boolean shouldTrackOutput() {
		return true;
	}

	@Override
	public boolean shouldBroadcastConsoleToOps() {
		return true;
	}

}
