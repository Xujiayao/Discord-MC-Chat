package top.xujiayao.mcdiscordchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import okhttp3.OkHttpClient;
import top.xujiayao.mcdiscordchat.listeners.DiscordEventListener;
import top.xujiayao.mcdiscordchat.listeners.MinecraftEventListener;
import top.xujiayao.mcdiscordchat.objects.Texts;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;
import top.xujiayao.mcdiscordchat.utils.Utils;

import java.util.Timer;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final OkHttpClient client = new OkHttpClient();

	public static JDA jda;
	public static TextChannel textChannel;
	public static TextChannel consoleLogTextChannel;
	public static Config config;
	public static Texts texts;

	public static boolean stop = false;

	public static Timer msptMonitorTimer;
	public static Timer consoleLogTimer1;
	public static Timer consoleLogTimer2;

	@Override
	public void onInitializeServer() {
		ConfigManager.initConfig();

		try {
			if (config.generic.membersIntents) {
				jda = JDABuilder.createDefault(config.generic.botToken)
						.setMemberCachePolicy(MemberCachePolicy.ALL)
						.enableIntents(GatewayIntent.GUILD_MEMBERS)
						.addEventListeners(new DiscordEventListener())
						.build();
			} else {
				jda = JDABuilder.createDefault(config.generic.botToken)
						.addEventListeners(new DiscordEventListener())
						.build();
			}

			jda.awaitReady();
			textChannel = jda.getTextChannelById(config.generic.channelId);
			
			if (!config.generic.consoleLogChannelId.isEmpty()) {
				consoleLogTextChannel = jda.getTextChannelById(config.generic.consoleLogChannelId);
			}
		} catch (Exception e) {
			e.printStackTrace();
			jda = null;
			Thread.currentThread().interrupt();
		}

		if (jda != null) {
			if (!config.generic.botListeningStatus.isEmpty()) {
				jda.getPresence().setActivity(Activity.listening(config.generic.botListeningStatus));
			}

			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				textChannel.sendMessage(texts.serverStarted()).queue();
				Utils.checkUpdate(false);

				if (config.generic.announceHighMSPT) {
					msptMonitorTimer = new Timer();
					Utils.monitorMSPT();
				}
			});

			ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
				stop = true;
				textChannel.sendMessage(texts.serverStopped()).queue();

				if (config.generic.announceHighMSPT) {
					msptMonitorTimer.cancel();
				}

				if (!config.generic.consoleLogChannelId.isEmpty()) {
					consoleLogTimer1.cancel();
					consoleLogTimer2.cancel();
				}

				try {
					Thread.sleep(250);
				} catch (Exception e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}

				jda.shutdown();

				try {
					Thread.sleep(250);
				} catch (Exception e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}
			});

			new MinecraftEventListener().init();
		}
	}
}
