package top.xujiayao.mcDiscordChat;

import com.mashape.unirest.http.Unirest;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import top.xujiayao.mcDiscordChat.commands.ShrugCommand;
import top.xujiayao.mcDiscordChat.listeners.DiscordEventListener;
import top.xujiayao.mcDiscordChat.listeners.MinecraftEventListener;

import java.util.Collections;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static Config config;
	public static JDA jda;
	public static TextChannel textChannel;

	public static boolean stop = false;

	@Override
	public void onInitializeServer() {
		config = new Config();

		try {
			if (config.membersIntents) {
				jda = JDABuilder.createDefault(config.botToken).setHttpClient(new OkHttpClient.Builder()
					  .protocols(Collections.singletonList(Protocol.HTTP_1_1))
					  .build())
					  .setMemberCachePolicy(MemberCachePolicy.ALL)
					  .enableIntents(GatewayIntent.GUILD_MEMBERS)
					  .addEventListeners(new DiscordEventListener())
					  .build();
			} else {
				jda = JDABuilder.createDefault(config.botToken).setHttpClient(new OkHttpClient.Builder()
					  .protocols(Collections.singletonList(Protocol.HTTP_1_1))
					  .build())
					  .addEventListeners(new DiscordEventListener())
					  .build();
			}

			jda.awaitReady();
			textChannel = jda.getTextChannelById(config.channelId);
		} catch (Exception e) {
			e.printStackTrace();
			jda = null;
		}

		if (jda != null) {
			if (!config.botListeningStatus.isEmpty()) {
				jda.getPresence().setActivity(Activity.listening(config.botListeningStatus));
			}

			ServerLifecycleEvents.SERVER_STARTED.register((server) -> textChannel.sendMessage(config.texts.serverStarted).queue());

			ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
				stop = true;
				textChannel.sendMessage(config.texts.serverStopped).queue();

				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				try {
					Unirest.shutdown();
				} catch (Exception e) {
					e.printStackTrace();
				}

				jda.shutdown();

				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});

			new MinecraftEventListener().init();
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			if (dedicated) {
				ShrugCommand.register(dispatcher);
			}
		});
	}
}
