package top.xujiayao.mcdiscordchat.utils;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
public class Utils {

	public static void reloadTexts() {
		if (CONFIG.generic.useEngInsteadOfChin) {
			TEXTS = new Texts(CONFIG.textsEN.serverStarted,
					CONFIG.textsEN.serverStopped,
					CONFIG.textsEN.joinServer,
					CONFIG.textsEN.leftServer,
					CONFIG.textsEN.deathMessage,
					CONFIG.textsEN.advancementTask,
					CONFIG.textsEN.advancementChallenge,
					CONFIG.textsEN.advancementGoal,
					CONFIG.textsEN.highMspt,
					CONFIG.textsEN.consoleLogMessage);
		} else {
			TEXTS = new Texts(CONFIG.textsZH.serverStarted,
					CONFIG.textsZH.serverStopped,
					CONFIG.textsZH.joinServer,
					CONFIG.textsZH.leftServer,
					CONFIG.textsZH.deathMessage,
					CONFIG.textsZH.advancementTask,
					CONFIG.textsZH.advancementChallenge,
					CONFIG.textsZH.advancementGoal,
					CONFIG.textsZH.highMspt,
					CONFIG.textsZH.consoleLogMessage);
		}

	}
}
