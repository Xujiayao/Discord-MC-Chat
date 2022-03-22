package top.xujiayao.mcdiscordchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.Texts;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
	public static final Logger LOGGER = LoggerFactory.getLogger("MCDiscordChat");
	public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat.json");
	public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
	public static String VERSION;
	public static Config CONFIG;
	public static JDA JDA;
	public static TextChannel CHANNEL;
	public static TextChannel CONSOLE_LOG_CHANNEL;
	public static Texts TEXTS;

	@Override
	public void onInitializeServer() {
		ConfigManager.init();
		Utils.setMcdcVersion();

		LOGGER.info("-----------------------------------------");
		LOGGER.info("MCDiscordChat (MCDC) " + VERSION);
		LOGGER.info("By Xujiayao");
		LOGGER.info("");
		LOGGER.info("More information + Docs:");
		LOGGER.info("https://blog.xujiayao.top/posts/4ba0a17a/");
		LOGGER.info("-----------------------------------------");

		try {
			JDA = JDABuilder.createDefault(CONFIG.generic.botToken)
					.setChunkingFilter(ChunkingFilter.ALL)
					.setMemberCachePolicy(MemberCachePolicy.ALL)
					.enableIntents(GatewayIntent.GUILD_MEMBERS)
//					.addEventListeners(...)
					.build();

			JDA.awaitReady();

			if (!CONFIG.generic.botListeningStatus.isEmpty()) {
				JDA.getPresence().setActivity(Activity.listening(CONFIG.generic.botListeningStatus));
			}

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

			Utils.checkUpdate(false);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
			CHANNEL.sendMessage(TEXTS.serverStopped()).queue();

			JDA.shutdown();
		});
	}
}
