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

	public static void reloadTextsConfig() {
		if (Main.config.generic.switchLanguageFromChinToEng) {
			Main.texts = new Texts(Main.config.textsEN.serverStarted,
					Main.config.textsEN.serverStopped,
					Main.config.textsEN.joinServer,
					Main.config.textsEN.leftServer,
					Main.config.textsEN.deathMessage,
					Main.config.textsEN.advancementTask,
					Main.config.textsEN.advancementChallenge,
					Main.config.textsEN.advancementGoal,
					Main.config.textsEN.highMSPT,
					Main.config.textsEN.consoleLogMessage,
					Main.config.textsEN.blueColoredText,
					Main.config.textsEN.roleColoredText,
					Main.config.textsEN.colorlessText);
		} else {
			Main.texts = new Texts(Main.config.textsZH.serverStarted,
					Main.config.textsZH.serverStopped,
					Main.config.textsZH.joinServer,
					Main.config.textsZH.leftServer,
					Main.config.textsZH.deathMessage,
					Main.config.textsZH.advancementTask,
					Main.config.textsZH.advancementChallenge,
					Main.config.textsZH.advancementGoal,
					Main.config.textsZH.highMSPT,
					Main.config.textsZH.consoleLogMessage,
					Main.config.textsZH.blueColoredText,
					Main.config.textsZH.roleColoredText,
					Main.config.textsZH.colorlessText);
		}
	}

	public static List<File> getFileList(File file) {
		List<File> result = new ArrayList<>();

		File[] directoryList = file.listFiles(file1 -> file1.isFile() && file1.getName().contains("json"));

		Collections.addAll(result, Objects.requireNonNull(directoryList));

		return result;
	}
}
