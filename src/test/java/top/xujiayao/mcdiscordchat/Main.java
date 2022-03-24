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

	public static Timer msptMonitorTimer;

	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (config.generic.announceHighMSPT) {
				msptMonitorTimer = new Timer();
				Utils.monitorMSPT();
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (config.generic.announceHighMSPT) {
				msptMonitorTimer.cancel();
			}

			jda.shutdown();
		});
	}
}
