package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import okhttp3.CacheControl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.multiServer.MultiServer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CHANNEL_TOPIC_MONITOR_TIMER;
import static top.xujiayao.mcdiscordchat.Main.CHECK_UPDATE_TIMER;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_THREAD;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.JDA;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.MSPT_MONITOR_TIMER;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER;
import static top.xujiayao.mcdiscordchat.Main.SERVER_STARTED_TIME;
import static top.xujiayao.mcdiscordchat.Main.UPDATE_NOTIFICATION_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.VERSION;

/**
 * @author Xujiayao
 */
public class Utils {

	public static boolean isAdmin(Member member) {
		if (CONFIG.generic.adminsIds.contains(member.getId())) {
			return true;
		}

		for (Role role : member.getRoles()) {
			if (CONFIG.generic.adminsIds.contains(role.getId())) {
				return true;
			}
		}

		return false;
	}

	public static String adminsMentionString() {
		StringBuilder text = new StringBuilder();

		for (String id : CONFIG.generic.adminsIds) {
			User user = JDA.getUserById(id);
			Role role = JDA.getRoleById(id);
			if (user != null) {
				text.append(user.getAsMention()).append(" ");
			} else if (role != null) {
				text.append(role.getAsMention()).append(" ");
			} else {
				throw new NullPointerException("User or role not found for target ID");
			}
		}

		return text.toString();
	}

	public static String checkUpdate(boolean isManualCheck) {
		try {
			Request request = new Request.Builder()
					.url("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version.json")
					.cacheControl(CacheControl.FORCE_NETWORK)
					.build();

			try (Response response = HTTP_CLIENT.newCall(request).execute()) {
				String result = Objects.requireNonNull(response.body()).string();

				JsonObject latestJson = new Gson().fromJson(result, JsonObject.class);

				CONFIG.latestVersion = latestJson.get("version").getAsString();
				ConfigManager.update();

				StringBuilder message = new StringBuilder();

				if (!CONFIG.latestVersion.equals(VERSION)) {
					if (CONFIG.latestCheckTime > (System.currentTimeMillis() - 172800000) && !isManualCheck) {
						return "";
					}

					CONFIG.latestCheckTime = System.currentTimeMillis();
					ConfigManager.update();

					message.append(Translations.translate("utils.utils.cUpdate.newVersionAvailable"));
					message.append("\n\n");
					message.append("MCDiscordChat **").append(VERSION).append("** -> **").append(CONFIG.latestVersion).append("**");
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.downloadLink"));
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.changelog")).append(latestJson.get("changelog").getAsString());
					message.append("\n\n");

					if (CONFIG.generic.mentionAdminsForUpdates) {
						message.append(adminsMentionString());
					}

					return message.toString();
				} else {
					message.append("MCDiscordChat **").append(VERSION).append("**");
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.upToDate"));

					return isManualCheck ? message.toString() : "";
				}
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			return "";
		}
	}

	public static String reload() {
		try {
			MSPT_MONITOR_TIMER.cancel();
			CHANNEL_TOPIC_MONITOR_TIMER.cancel();
			CHECK_UPDATE_TIMER.cancel();

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.bye();
				MULTI_SERVER.stopMultiServer();
			}

			ConfigManager.init(true);
			Translations.init();

			Utils.setBotActivity();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			CONSOLE_LOG_THREAD.interrupt();
			CONSOLE_LOG_THREAD.join(5000);
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
				CONSOLE_LOG_THREAD = new Thread(new ConsoleLogListener(false));
				CONSOLE_LOG_THREAD.start();
			}
			if (!CONFIG.generic.updateNotificationChannelId.isEmpty()) {
				UPDATE_NOTIFICATION_CHANNEL = JDA.getTextChannelById(CONFIG.generic.updateNotificationChannelId);
			}
			if (UPDATE_NOTIFICATION_CHANNEL == null) {
				UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
			}

			Utils.updateBotCommands();

			CHECK_UPDATE_TIMER = new Timer();
			Utils.initCheckUpdateTimer();

			MSPT_MONITOR_TIMER = new Timer();
			if (CONFIG.generic.announceHighMspt) {
				Utils.initMsptMonitor();
			}

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER = new MultiServer();
				MULTI_SERVER.start();
			}

			CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
			if (CONFIG.generic.updateChannelTopic) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						if (!CONFIG.multiServer.enable) {
							Utils.initChannelTopicMonitor();
						} else if (MULTI_SERVER.server != null) {
							MULTI_SERVER.initMultiServerChannelTopicMonitor();
						}
					}
				}, 2000);
			}

			return Translations.translate("utils.utils.reload.success");
		} catch (Exception ex) {
			LOGGER.error(ExceptionUtils.getStackTrace(ex));
			return Translations.translate("utils.utils.reload.fail");
		}
	}

	public static String getInfoCommandMessage() {
		StringBuilder message = new StringBuilder()
				.append("=============== ")
				.append(Translations.translate("utils.utils.gicMessage.serverStatus"))
				.append(" ===============\n\n");

		// Online players
		List<ServerPlayerEntity> onlinePlayers = SERVER.getPlayerManager().getPlayerList();
		message.append(Translations.translate("utils.utils.gicMessage.onlinePlayers", onlinePlayers.size(), SERVER.getPlayerManager().getMaxPlayerCount()));

		if (onlinePlayers.isEmpty()) {
			message.append(Translations.translate("utils.utils.gicMessage.noPlayersOnline"));
		} else {
			for (ServerPlayerEntity player : onlinePlayers) {
				message.append("[").append(player.pingMilliseconds).append("ms] ").append(player.getEntityName()).append("\n");
			}
		}

		// Server TPS
		double serverTickTime = MathHelper.average(SERVER.lastTickLengths) * 1.0E-6D;
		message.append(Translations.translate("utils.utils.gicMessage.serverTps", String.format("%.2f", Math.min(1000.0 / serverTickTime, 20))));

		// Server MSPT
		message.append(Translations.translate("utils.utils.gicMessage.serverMspt", String.format("%.2f", serverTickTime)));

		// Server used memory
		message.append(Translations.translate("utils.utils.gicMessage.serverUsedMemory", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024, Runtime.getRuntime().totalMemory() / 1024 / 1024));

		return message.toString();
	}

	public static String getStatsCommandMessage(String type, String name) {
		StringBuilder message = new StringBuilder()
				.append("=============== ")
				.append(Translations.translate("utils.utils.gscMessage.scoreboard"))
				.append(" ===============\n");

		Map<String, Integer> stats = new HashMap<>();

		try {
			JsonArray players = new Gson().fromJson(IOUtils.toString(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath() + "/usercache.json").toURI(), StandardCharsets.UTF_8), JsonArray.class);

			//#if MC >= 11600
			FileUtils.listFiles(new File((SERVER.getSaveProperties().getLevelName() + "/stats/")), null, false).forEach(file -> {
			//#else
			//$$ FileUtils.listFiles(new File((SERVER.getLevelName() + "/stats/")), null, false).forEach(file -> {
			//#endif
				try {
					for (JsonElement player : players) {
						if (player.getAsJsonObject().get("uuid").getAsString().equals(file.getName().replace(".json", ""))) {
							JsonObject json = new Gson().fromJson(IOUtils.toString(file.toURI(), StandardCharsets.UTF_8), JsonObject.class);

							try {
								stats.put(player.getAsJsonObject().get("name").getAsString(), json
										.getAsJsonObject("stats")
										.getAsJsonObject("minecraft:" + type)
										.get("minecraft:" + name)
										.getAsInt());
							} catch (NullPointerException ignored) {
							}
						}
					}
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}
			});
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

		if (stats.isEmpty()) {
			message.append(Translations.translate("utils.utils.gscMessage.noResult"));
		} else {
			List<Map.Entry<String, Integer>> sortedlist = new ArrayList<>(stats.entrySet());
			sortedlist.sort((c1, c2) -> c2.getValue().compareTo(c1.getValue()));

			for (Map.Entry<String, Integer> entry : sortedlist) {
				message.append(String.format("\n%-8d %-8s", entry.getValue(), entry.getKey()));
			}
		}

		return message.toString();
	}

	public static void setBotActivity() {
		if (!CONFIG.generic.botPlayingStatus.isEmpty()) {
			JDA.getPresence().setActivity(Activity.playing(CONFIG.generic.botPlayingStatus));
		} else if (!CONFIG.generic.botListeningStatus.isEmpty()) {
			JDA.getPresence().setActivity(Activity.listening(CONFIG.generic.botListeningStatus));
		} else {
			JDA.getPresence().setActivity(null);
		}
	}

	public static void updateBotCommands() {
		JDA.updateCommands()
				.addCommands(Commands.slash("info", Translations.translate("utils.utils.ubCommands.info")))
				.addCommands(Commands.slash("help", Translations.translate("utils.utils.ubCommands.help")))
				.addCommands(Commands.slash("update", Translations.translate("utils.utils.ubCommands.update")))
				.addCommands(Commands.slash("stats", Translations.translate("utils.utils.ubCommands.stats"))
						.addOption(OptionType.STRING, "type", Translations.translate("utils.utils.ubCommands.stats.type"), true)
						.addOption(OptionType.STRING, "name", Translations.translate("utils.utils.ubCommands.stats.name"), true))
				.addCommands(Commands.slash("reload", Translations.translate("utils.utils.ubCommands.reload")))
				.addCommands(Commands.slash("console", Translations.translate("utils.utils.ubCommands.console"))
						.addOption(OptionType.STRING, "command", Translations.translate("utils.utils.ubCommands.console.command"), true))
				.addCommands(Commands.slash("log", Translations.translate("utils.utils.ubCommands.log"))
						.addOption(OptionType.STRING, "file", Translations.translate("utils.utils.ubCommands.log.file"), true, true))
				.addCommands(Commands.slash("stop", Translations.translate("utils.utils.ubCommands.stop")))
				.queue();
	}

	public static void initMsptMonitor() {
		MSPT_MONITOR_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				double mspt = MathHelper.average(SERVER.lastTickLengths) * 1.0E-6D;

				if (mspt > CONFIG.generic.msptLimit) {
					CHANNEL.sendMessage(Translations.translateMessage("message.highMspt")
							.replace("%mspt%", String.format("%.2f", mspt))
							.replace("%msptLimit%", Integer.toString(CONFIG.generic.msptLimit))).queue();
					if (CONFIG.multiServer.enable) {
						MULTI_SERVER.sendMessage(false, false, false, null, MarkdownParser.parseMarkdown(Translations.translateMessage("message.highMspt")
								.replace("%mspt%", String.format("%.2f", mspt))
								.replace("%msptLimit%", Integer.toString(CONFIG.generic.msptLimit))));
					}
				}
			}
		}, 0, CONFIG.generic.msptCheckInterval);
	}

	public static void initChannelTopicMonitor() {
		CHANNEL_TOPIC_MONITOR_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				String topic = Translations.translateMessage("message.onlineChannelTopic")
						.replace("%onlinePlayerCount%", Integer.toString(SERVER.getPlayerManager().getPlayerList().size()))
						.replace("%maxPlayerCount%", Integer.toString(SERVER.getPlayerManager().getMaxPlayerCount()))
						//#if MC >= 11600
						.replace("%uniquePlayerCount%", Integer.toString(FileUtils.listFiles(new File((SERVER.getSaveProperties().getLevelName() + "/stats/")), null, false).size()))
						//#else
						//$$ .replace("%uniquePlayerCount%", Integer.toString(FileUtils.listFiles(new File((SERVER.getLevelName() + "/stats/")), null, false).size()))
						//#endif
						.replace("%serverStartedTime%", SERVER_STARTED_TIME)
						.replace("%lastUpdateTime%", Long.toString(Instant.now().getEpochSecond()));

				CHANNEL.getManager().setTopic(topic).queue();

				if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
					CONSOLE_LOG_CHANNEL.getManager().setTopic(topic).queue();
				}
			}
		}, 0, CONFIG.generic.channelTopicUpdateInterval);
	}

	public static void initCheckUpdateTimer() {
		CHECK_UPDATE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				if (CONFIG.latestCheckTime > System.currentTimeMillis()) {
					CONFIG.latestCheckTime = System.currentTimeMillis() - 300000000;
					ConfigManager.update();
				}

				String message = checkUpdate(false);
				if (!message.isEmpty()) {
				// TODO if (!message.isEmpty() && CONFIG.generic.notifyUpdates) {
					UPDATE_NOTIFICATION_CHANNEL.sendMessage(message).queue();
				}
			}
		}, 3600000, 21600000);
	}
}
