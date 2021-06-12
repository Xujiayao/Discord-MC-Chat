package top.xujiayao.mcDiscordChat;

import kong.unirest.Unirest;
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

	public static JDA jda;
	public static TextChannel textChannel;

	public static boolean stop = false;

	@Override
	public void onInitializeServer() {
		try {
			if (Config.membersIntents) {
				jda = JDABuilder.createDefault(Config.botToken).setHttpClient(new OkHttpClient.Builder()
					  .protocols(Collections.singletonList(Protocol.HTTP_1_1))
					  .build())
					  .setMemberCachePolicy(MemberCachePolicy.ALL)
					  .enableIntents(GatewayIntent.GUILD_MEMBERS)
					  .addEventListeners(new DiscordEventListener())
					  .build();
			} else {
				jda = JDABuilder.createDefault(Config.botToken).setHttpClient(new OkHttpClient.Builder()
					  .protocols(Collections.singletonList(Protocol.HTTP_1_1))
					  .build())
					  .addEventListeners(new DiscordEventListener())
					  .build();
			}

			jda.awaitReady();
			textChannel = jda.getTextChannelById(Config.channelId);
		} catch (Exception e) {
			e.printStackTrace();
			jda = null;
		}

		if (jda != null) {
			if (!Config.botListeningStatus.isEmpty()) {
				jda.getPresence().setActivity(Activity.listening(Config.botListeningStatus));
			}

			ServerLifecycleEvents.SERVER_STARTED.register((server) -> textChannel.sendMessage(Config.serverStarted).queue());

			ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
				stop = true;
				textChannel.sendMessage(Config.serverStopped).queue();

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

		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			if (dedicated) {
				ShrugCommand.register(dispatcher);
			}
		});
	}
}
