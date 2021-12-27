package top.xujiayao.mcdiscordchat;

import kong.unirest.Unirest;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

	public static JDA jda;
	public static TextChannel textChannel;
	public static Config config;
	public static Texts texts;

	public static boolean stop = false;

	public static Timer msptMonitorTimer;

	@Override
	public void onInitializeServer() {
		ConfigManager.initConfig();

		try {
			if (Main.config.generic.membersIntents) {
				jda = JDABuilder.createDefault(Main.config.generic.botToken)
					  .setMemberCachePolicy(MemberCachePolicy.ALL)
					  .enableIntents(GatewayIntent.GUILD_MEMBERS)
					  .addEventListeners(new DiscordEventListener())
					  .build();
			} else {
				jda = JDABuilder.createDefault(Main.config.generic.botToken)
					  .addEventListeners(new DiscordEventListener())
					  .build();
			}

			jda.awaitReady();
			textChannel = jda.getTextChannelById(Main.config.generic.channelId);
		} catch (Exception e) {
			e.printStackTrace();
			jda = null;
		}

		if (jda != null) {
			if (!Main.config.generic.botListeningStatus.isEmpty()) {
				jda.getPresence().setActivity(Activity.listening(Main.config.generic.botListeningStatus));
			}

			ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
				textChannel.sendMessage(Main.texts.serverStarted()).queue();
				Utils.checkUpdate(false);

				if (config.generic.announceHighMSPT) {
					msptMonitorTimer = new Timer();
					Utils.monitorMSPT();
				}
			});

			ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
				stop = true;
				textChannel.sendMessage(Main.texts.serverStopped()).queue();

				if (config.generic.announceHighMSPT) {
					msptMonitorTimer.cancel();
				}

				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				Unirest.shutDown();

				jda.shutdown();

				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});

			new MinecraftEventListener().init();
		}
	}
}
