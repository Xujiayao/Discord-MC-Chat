package top.xujiayao.mcdiscordchat.utils;

import net.minecraft.util.math.MathHelper;
import top.xujiayao.mcdiscordchat.Main;

import java.util.Objects;
import java.util.TimerTask;

/**
 * @author Xujiayao
 */
public class Utils {

	public static void monitorMSPT() {
		Main.msptMonitorTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				double mspt = MathHelper.average(Objects.requireNonNull(getServer()).lastTickLengths) * 1.0E-6D;

				if (mspt > Main.config.generic.msptLimit) {
					Main.textChannel.sendMessage(Main.texts.highMSPT()
							.replace("%mspt%", Double.toString(mspt))
							.replace("%msptLimit%", Integer.toString(Main.config.generic.msptLimit))).queue();
				}
			}
		}, 0, 5000);
	}
}
