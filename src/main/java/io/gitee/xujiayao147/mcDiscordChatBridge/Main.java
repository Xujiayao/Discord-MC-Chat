package io.gitee.xujiayao147.mcDiscordChatBridge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.gitee.xujiayao147.mcDiscordChatBridge.commands.ShrugCommand;
import io.gitee.xujiayao147.mcDiscordChatBridge.listeners.DiscordEventListener;
import io.gitee.xujiayao147.mcDiscordChatBridge.listeners.MinecraftEventListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final String MOD_ID = "mc-discord-chat-bridge";
	public static Logger logger = LogManager.getLogger(MOD_ID);
	public static Config config;
	public static JDA jda;
	public static TextChannel textChannel;

	@Override
	public void onInitializeServer() {
		config = new Config();
		
		try {
			if (config.membersIntents) {
				jda = JDABuilder.createDefault(config.botToken).setMemberCachePolicy(MemberCachePolicy.ALL)
						.enableIntents(GatewayIntent.GUILD_MEMBERS).addEventListeners(new DiscordEventListener())
						.build();
			} else {
				jda = JDABuilder.createDefault(config.botToken).addEventListeners(new DiscordEventListener())
						.build();
			}

			jda.awaitReady();
			textChannel = jda.getTextChannelById(config.channelId);
		} catch (Exception e) {
			jda = null;
			logger.error(e);
		}

		if (jda != null) {
			if (!config.botListeningStatus.isEmpty())
				jda.getPresence().setActivity(Activity.listening(config.botListeningStatus));

			ServerLifecycleEvents.SERVER_STARTED
					.register((server) -> textChannel.sendMessage(config.texts.serverStarted).queue());
			ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
				textChannel.sendMessage(config.texts.serverStopped).queue();
				jda.shutdown();
			});
			ServerLifecycleEvents.SERVER_STOPPED.register((server) -> jda.shutdownNow());

			new MinecraftEventListener().init();
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			if (dedicated) {
				ShrugCommand.register(dispatcher);
			}
		});
	}
}
