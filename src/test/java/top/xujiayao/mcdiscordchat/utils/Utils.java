package top.xujiayao.mcdiscordchat.utils;

import net.minecraft.util.math.MathHelper;
import top.xujiayao.mcdiscordchat.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

	public static List<File> getFileList(File file) {
		List<File> result = new ArrayList<>();

		File[] directoryList = file.listFiles(file1 -> file1.isFile() && file1.getName().contains("json"));

		Collections.addAll(result, Objects.requireNonNull(directoryList));

		return result;
	}
}
