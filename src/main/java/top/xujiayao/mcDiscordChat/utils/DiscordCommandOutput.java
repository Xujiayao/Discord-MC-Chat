package top.xujiayao.mcDiscordChat.utils;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;
import top.xujiayao.mcDiscordChat.Main;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * @author Xujiayao
 */
public class DiscordCommandOutput implements CommandOutput {

	private StringBuilder outputString = new StringBuilder().append("```");
	private long lastOutputMillis = 0;

	@Override
	public void sendSystemMessage(Text message, UUID senderUuid) {
		String messageString = message.getString();
		long currentOutputMillis = System.currentTimeMillis();

		if ((outputString.length() + messageString.length()) > 2000) {
			outputString.append("```");
			Main.textChannel.sendMessage(outputString).queue();
			outputString = new StringBuilder("```\n");
		} else {
			outputString.append(messageString).append("\n");
		}

		if ((currentOutputMillis - lastOutputMillis) > 50) {
			new Thread(() -> new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					outputString.append("```");
					Main.textChannel.sendMessage(outputString).queue();
					outputString = new StringBuilder("```\n");
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
