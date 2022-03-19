package top.xujiayao.mcdiscordchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.Texts;

import java.io.File;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("MCDiscordChat");
	public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat.json");
	// ------------------------------------
	public static String VERSION = "2.0.0";
	// ------------------------------------
	public static Config CONFIG;
	public static JDA JDA;
	public static TextChannel CHANNEL;
	public static TextChannel CONSOLE_LOG_CHANNEL;
	public static Texts TEXTS;

	@Override
	public void onInitializeServer() {
		ConfigManager.init();

		LOGGER.info("-----------------------------------------");
		LOGGER.info("MCDiscordChat (MCDC) " + VERSION);
		LOGGER.info("By Xujiayao");
		LOGGER.info("");
		LOGGER.info("More information + Docs:");
		LOGGER.info("https://blog.xujiayao.top/posts/4ba0a17a/");
		LOGGER.info("-----------------------------------------");

		try {
			JDA = JDABuilder.createDefault(CONFIG.generic.botToken)
					.setActivity(Activity.listening(CONFIG.generic.botListeningStatus))
//					.addEventListeners(...)
					.build();

			JDA.awaitReady();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			System.exit(1);
		}

		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			CHANNEL.sendMessage(TEXTS.serverStarted()).queue();

//			Check Update
		});

		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
			CHANNEL.sendMessage(TEXTS.serverStopped()).queue();

			JDA.shutdown();
		});

//		Init MCEventListener
	}
}
