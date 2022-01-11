package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import kong.unirest.Unirest;
import net.dv8tion.jda.api.entities.Member;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.Main;
import top.xujiayao.mcdiscordchat.objects.ModJson;
import top.xujiayao.mcdiscordchat.objects.Texts;
import top.xujiayao.mcdiscordchat.objects.Version;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
public class Utils {

	private Utils() {
		throw new IllegalStateException("Utility class");
	}

	public static String adminsMentionString() {
		StringBuilder text = new StringBuilder();

		for (String id : Main.config.generic.superAdminsIds) {
			text.append("<@").append(id).append("> ");
		}

		for (String id : Main.config.generic.adminsIds) {
			text.append("<@").append(id).append("> ");
		}

		return text.toString();
	}

	public static MinecraftServer getServer() {
		@SuppressWarnings("deprecation")
		Object gameInstance = FabricLoader.getInstance().getGameInstance();

		if (gameInstance instanceof MinecraftServer minecraftServer) {
			return minecraftServer;
		}

		return null;
	}

	public static void monitorMSPT() {
		Main.msptMonitorTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				double mspt = MathHelper.average(Objects.requireNonNull(getServer()).lastTickLengths) * 1.0E-6D;

				if (mspt > Main.config.generic.msptLimit) {
					Main.textChannel.sendMessage(Main.texts.highMSPT()
						  .replace("%mspt%", Double.toString(mspt))
						  .replace("%msptLimit%", Integer.toString(Main.config.generic.msptLimit))
						  .replace("%mentionAllAdmins%", adminsMentionString())).queue();
				}
			}
		}, 0, 5000);
	}

	public static void checkUpdate(boolean isManualCheck) {
		try {
			Version version = new Gson().fromJson(Unirest.get("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version.json").asString().getBody(), Version.class);
			ModJson modJson = new Gson().fromJson(IOUtils.toString(new URI("jar:file:" + Main.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "!/fabric.mod.json"), StandardCharsets.UTF_8), ModJson.class);

			if (!version.version().equals(modJson.version().substring(modJson.version().indexOf("-") + 1))) {
				StringBuilder text;

				if (Main.config.generic.switchLanguageFromChinToEng) {
					text = new StringBuilder("**A new version is available!**\n\nMCDiscordChat **" + modJson.version().substring(modJson.version().indexOf("-") + 1) + "** -> **" + version.version() + "**\n\nDownload link: https://github.com/Xujiayao/MCDiscordChat/blob/master/README.md#Download\n\n");
				} else {
					text = new StringBuilder("**新版本可用！**\n\nMCDiscordChat **" + modJson.version().substring(modJson.version().indexOf("-") + 1) + "** -> **" + version.version() + "**\n\n下载链接：https://github.com/Xujiayao/MCDiscordChat/blob/master/README_CN.md#%E4%B8%8B%E8%BD%BD\n\n");
				}

				text.append(adminsMentionString());

				Main.textChannel.sendMessage(text).queue();
			} else {
				if (isManualCheck) {
					Main.textChannel.sendMessage("MCDiscordChat **" + modJson.version().substring(modJson.version().indexOf("-") + 1) + (Main.config.generic.switchLanguageFromChinToEng ? "**\n\n**MCDiscordChat is up to date!**" : "**\n\n**当前版本已经是最新版本！**")).queue();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			Main.textChannel.sendMessage("```\n" + ExceptionUtils.getStackTrace(e) + "\n```").queue();
		}
	}

	public static void reloadTextsConfig() {
		if (Main.config.generic.switchLanguageFromChinToEng) {
			Main.texts = new Texts(Main.config.textsEN.serverStarted,
				  Main.config.textsEN.serverStopped,
				  Main.config.textsEN.joinServer,
				  Main.config.textsEN.leftServer,
				  Main.config.textsEN.deathMessage,
				  Main.config.textsEN.advancementTask,
				  Main.config.textsEN.advancementChallenge,
				  Main.config.textsEN.advancementGoal,
				  Main.config.textsEN.highMSPT,
				  Main.config.textsEN.blueColoredText,
				  Main.config.textsEN.roleColoredText,
				  Main.config.textsEN.colorlessText);
		} else {
			Main.texts = new Texts(Main.config.textsZH.serverStarted,
				  Main.config.textsZH.serverStopped,
				  Main.config.textsZH.joinServer,
				  Main.config.textsZH.leftServer,
				  Main.config.textsZH.deathMessage,
				  Main.config.textsZH.advancementTask,
				  Main.config.textsZH.advancementChallenge,
				  Main.config.textsZH.advancementGoal,
				  Main.config.textsZH.highMSPT,
				  Main.config.textsZH.blueColoredText,
				  Main.config.textsZH.roleColoredText,
				  Main.config.textsZH.colorlessText);
		}
	}

	public static List<File> getFileList(File file) {
		List<File> result = new ArrayList<>();

		File[] directoryList = file.listFiles(file1 -> file1.isFile() && file1.getName().contains("json"));

		Collections.addAll(result, Objects.requireNonNull(directoryList));

		return result;
	}

	public static Pair<String, String> convertMentionsFromNames(String message) {
		if (!message.contains("@")) {
			return new Pair<>(message, message);
		}

		List<String> messageList = Arrays.asList(message.split("@[\\S]+"));

		if (messageList.isEmpty()) {
			messageList = new ArrayList<>();
			messageList.add("");
		}

		StringBuilder discordString = new StringBuilder();
		StringBuilder mcString = new StringBuilder();

		Pattern pattern = Pattern.compile("@[\\S]+");
		Matcher matcher = pattern.matcher(message);

		int x = 0;

		while (matcher.find()) {
			Member member = null;

			for (Member m : Main.textChannel.getMembers()) {
				String name = matcher.group().substring(1);

				if (m.getUser().getName().equalsIgnoreCase(name) || (m.getNickname() != null && m.getNickname().equalsIgnoreCase(name))) {
					member = m;
				}
			}

			if (member == null) {
				discordString.append(messageList.get(x)).append(matcher.group());
				mcString.append(messageList.get(x)).append(matcher.group());
			} else {
				discordString.append(messageList.get(x)).append(member.getAsMention());
				mcString.append(messageList.get(x)).append(Formatting.YELLOW).append("@")
					  .append(member.getEffectiveName()).append(Formatting.WHITE);
			}

			x++;
		}

		if (x < messageList.size()) {
			discordString.append(messageList.get(x));
			mcString.append(messageList.get(x));
		}

		return new Pair<>(discordString.toString(), mcString.toString());
	}
}
