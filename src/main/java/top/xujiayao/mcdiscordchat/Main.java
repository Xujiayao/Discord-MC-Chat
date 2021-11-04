package top.xujiayao.mcdiscordchat;

import com.google.gson.Gson;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.commands.ShrugCommand;
import top.xujiayao.mcdiscordchat.listeners.DiscordEventListener;
import top.xujiayao.mcdiscordchat.listeners.MinecraftEventListener;
import top.xujiayao.mcdiscordchat.objects.ModJson;
import top.xujiayao.mcdiscordchat.objects.Texts;
import top.xujiayao.mcdiscordchat.objects.Version;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static JDA jda;
	public static TextChannel textChannel;
	public static Config config;
	public static Texts texts;

	public static boolean stop = false;

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

				try {
					Version version = new Gson().fromJson(Unirest.get("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version.json").asString().getBody(), Version.class);
					ModJson modJson = new Gson().fromJson(IOUtils.toString(new URI("jar:file:" + Main.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "!/fabric.mod.json"), StandardCharsets.UTF_8), ModJson.class);

					if (!version.version().equals(modJson.version)) {
						StringBuilder text;

						if (config.generic.switchLanguageFromChinToEng) {
							text = new StringBuilder("**A new version is available!**\n\nMCDiscordChat **" + modJson.version + "** -> **" + version.version() + "**\n\nDownload link: https://github.com/Xujiayao/MCDiscordChat/releases\n\n");
						} else {
							text = new StringBuilder("**新版本可用！**\n\nMCDiscordChat **" + modJson.version + "** -> **" + version.version() + "**\n\n下载链接：https://github.com/Xujiayao/MCDiscordChat/releases\n\n");
						}

						for (String id : config.generic.superAdminsIds) {
							text.append("<@").append(id).append("> ");
						}

						for (String id : config.generic.adminsIds) {
							text.append("<@").append(id).append("> ");
						}

						textChannel.sendMessage(text).queue();
					}
				} catch (Exception e) {
					e.printStackTrace();
					textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(e) + "\n```").queue();
				}
			});

			ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
				stop = true;
				textChannel.sendMessage(Main.texts.serverStopped()).queue();

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
