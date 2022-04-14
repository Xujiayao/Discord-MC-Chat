package top.xujiayao.mcdiscordchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xujiayao.mcdiscordchat.discord.DiscordEventListener;
import top.xujiayao.mcdiscordchat.multiServer.MultiServer;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.Texts;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Timer;

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
	public static long MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
	public static int MINECRAFT_SEND_COUNT = 0;
	public static MinecraftServer SERVER;
	public static Timer MSPT_MONITOR_TIMER = new Timer();
	public static Timer CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
	public static MultiServer MULTI_SERVER;
	public static String SERVER_STARTED_TIME;

	@Override
	public void onInitializeServer() {
		try {
			ConfigManager.init(false);
			Utils.setMcdcVersion();

			LOGGER.info("-----------------------------------------");
			LOGGER.info("MCDiscordChat (MCDC) " + VERSION);
			LOGGER.info("By Xujiayao");
			LOGGER.info("");
			LOGGER.info("More information + Docs:");
			LOGGER.info("https://blog.xujiayao.top/posts/4ba0a17a/");
			LOGGER.info("-----------------------------------------");

			JDA = JDABuilder.createDefault(CONFIG.generic.botToken)
					.setChunkingFilter(ChunkingFilter.ALL)
					.setMemberCachePolicy(MemberCachePolicy.ALL)
					.enableIntents(GatewayIntent.GUILD_MEMBERS)
					.addEventListeners(new DiscordEventListener())
					.build();

			JDA.awaitReady();

			Utils.setBotActivity();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
			}

			Utils.updateBotCommands();
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			System.exit(1);
		}

		if (CONFIG.multiServer.enable) {
			MULTI_SERVER = new MultiServer();
			MULTI_SERVER.start();
		}

		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			SERVER_STARTED_TIME = Long.toString(Instant.now().getEpochSecond());

			CHANNEL.sendMessage(TEXTS.serverStarted()).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, null, TEXTS.serverStarted());
			}

			SERVER = server;

			String message = Utils.checkUpdate(false);
			if (!message.isEmpty()) {
				CHANNEL.sendMessage(message).queue();
			}

			if (CONFIG.generic.announceHighMspt) {
				Utils.initMsptMonitor();
			}

			if (CONFIG.generic.updateChannelTopic) {
				Utils.initChannelTopicMonitor();
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
			MSPT_MONITOR_TIMER.cancel();
			CHANNEL_TOPIC_MONITOR_TIMER.cancel();

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, null, TEXTS.serverStopped());
				MULTI_SERVER.bye();
				MULTI_SERVER.stopMultiServer();
			}

			if (CONFIG.generic.updateChannelTopic && !CONFIG.multiServer.enable) {
				CHANNEL.sendMessage(TEXTS.serverStopped())
						.submit()
						.whenComplete((v, ex) -> {
							String topic = TEXTS.offlineChannelTopic()
									.replace("%lastUpdateTime%", Long.toString(Instant.now().getEpochSecond()));

							CHANNEL.getManager().setTopic(topic)
									.submit()
									.whenComplete((v2, ex2) -> {
										if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
											CONSOLE_LOG_CHANNEL.getManager().setTopic(topic)
													.submit()
													.whenComplete((v3, ex3) -> JDA.shutdownNow());
										} else {
											JDA.shutdownNow();
										}
									});
						});
			} else {
				CHANNEL.sendMessage(TEXTS.serverStopped())
						.submit()
						.whenComplete((v, ex) -> JDA.shutdownNow());
			}
		});
	}
}
