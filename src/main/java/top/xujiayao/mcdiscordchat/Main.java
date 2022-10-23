package top.xujiayao.mcdiscordchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
//#if MC >= 11900
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//#else
//$$ import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
//#if MC >= 11700
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.Logger;
//$$ import org.apache.logging.log4j.LogManager;
//#endif
import top.xujiayao.mcdiscordchat.discord.DiscordEventListener;
import top.xujiayao.mcdiscordchat.minecraft.MinecraftCommands;
import top.xujiayao.mcdiscordchat.multiServer.MultiServer;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.ConsoleLogListener;
import top.xujiayao.mcdiscordchat.utils.Translations;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.io.File;
import java.time.Instant;
import java.util.Timer;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
	//#if MC >= 11700
	public static final Logger LOGGER = LoggerFactory.getLogger("MCDiscordChat");
	//#else
	//$$ public static final Logger LOGGER = LogManager.getLogger("MCDiscordChat");
	//#endif
	public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat.json");
	public static final File CONFIG_BACKUP_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcdiscordchat-backup.json");
	public static final String VERSION = FabricLoader.getInstance().getModContainer("mcdiscordchat").orElseThrow().getMetadata().getVersion().getFriendlyString();
	public static Config CONFIG;
	public static JDA JDA;
	public static TextChannel CHANNEL;
	public static TextChannel CONSOLE_LOG_CHANNEL;
	public static Thread CONSOLE_LOG_THREAD = new Thread(new ConsoleLogListener(true));
	public static TextChannel UPDATE_NOTIFICATION_CHANNEL;
	public static long MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
	public static int MINECRAFT_SEND_COUNT = 0;
	public static MinecraftServer SERVER;
	public static Timer MSPT_MONITOR_TIMER = new Timer();
	public static Timer CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
	public static Timer CHECK_UPDATE_TIMER = new Timer();
	public static MultiServer MULTI_SERVER;
	public static String SERVER_STARTED_TIME;

	@Override
	public void onInitializeServer() {
		try {
			ConfigManager.init(false);
			Translations.init();

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
					.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
					.addEventListeners(new DiscordEventListener())
					.build();

			JDA.awaitReady();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
				CONSOLE_LOG_THREAD.start();
			}
			if (!CONFIG.generic.updateNotificationChannelId.isEmpty()) {
				UPDATE_NOTIFICATION_CHANNEL = JDA.getTextChannelById(CONFIG.generic.updateNotificationChannelId);
			}
			if (UPDATE_NOTIFICATION_CHANNEL == null || !UPDATE_NOTIFICATION_CHANNEL.canTalk()) {
				UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
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

		//#if MC >= 11900
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MinecraftCommands.register(dispatcher));
		//#else
		//$$ CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> MinecraftCommands.register(dispatcher));
		//#endif

		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			SERVER_STARTED_TIME = Long.toString(Instant.now().getEpochSecond());

			if (CONFIG.generic.announceServerStartStop) {
				CHANNEL.sendMessage(Translations.translateMessage("message.serverStarted")).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.serverStarted"));
				}
			}

			SERVER = server;

			Utils.setBotActivity();

			Utils.initCheckUpdateTimer();

			if (CONFIG.generic.announceHighMspt) {
				Utils.initMsptMonitor();
			}

			if (CONFIG.generic.updateChannelTopic) {
				if (!CONFIG.multiServer.enable) {
					Utils.initChannelTopicMonitor();
				} else if (MULTI_SERVER.server != null) {
					MULTI_SERVER.initMultiServerChannelTopicMonitor();
				}
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
			MSPT_MONITOR_TIMER.cancel();
			CHANNEL_TOPIC_MONITOR_TIMER.cancel();
			CHECK_UPDATE_TIMER.cancel();

			CONSOLE_LOG_THREAD.interrupt();
			try {
				CONSOLE_LOG_THREAD.join(5000);
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}

			if (CONFIG.multiServer.enable) {
				if (CONFIG.generic.announceServerStartStop) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.serverStopped"));
				}
				MULTI_SERVER.bye();
				MULTI_SERVER.stopMultiServer();
			}

			if (CONFIG.generic.updateChannelTopic) {
				if (CONFIG.generic.announceServerStartStop) {
					CHANNEL.sendMessage(Translations.translateMessage("message.serverStopped"))
							.submit()
							.whenComplete((v, ex) -> {
								String topic = Translations.translateMessage("message.offlineChannelTopic")
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
					String topic = Translations.translateMessage("message.offlineChannelTopic")
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
				}
			} else {
				if (CONFIG.generic.announceServerStartStop) {
					CHANNEL.sendMessage(Translations.translateMessage("message.serverStopped"))
							.submit()
							.whenComplete((v, ex) -> JDA.shutdownNow());
				} else {
					JDA.shutdownNow();
				}
			}
		});
	}
}
