package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import kong.unirest.Unirest;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
public class Utils {

	public static void checkUpdate(boolean isManualCheck) {
		try {
			Version version = new Gson().fromJson(Unirest.get("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version.json").asString().getBody(), Version.class);
			ModJson modJson = new Gson().fromJson(IOUtils.toString(new URI("jar:file:" + Main.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "!/fabric.mod.json"), StandardCharsets.UTF_8), ModJson.class);

			if (!version.version().equals(modJson.version)) {
				StringBuilder text;

				if (Main.config.generic.switchLanguageFromChinToEng) {
					text = new StringBuilder("**A new version is available!**\n\nMCDiscordChat **" + modJson.version + "** -> **" + version.version() + "**\n\nDownload link: https://github.com/Xujiayao/MCDiscordChat/releases\n\n");
				} else {
					text = new StringBuilder("**新版本可用！**\n\nMCDiscordChat **" + modJson.version + "** -> **" + version.version() + "**\n\n下载链接：https://github.com/Xujiayao/MCDiscordChat/releases\n\n");
				}

				for (String id : Main.config.generic.superAdminsIds) {
					text.append("<@").append(id).append("> ");
				}

				for (String id : Main.config.generic.adminsIds) {
					text.append("<@").append(id).append("> ");
				}

				Main.textChannel.sendMessage(text).queue();
			} else {
				if (isManualCheck) {
					Main.textChannel.sendMessage("**" + (Main.config.generic.switchLanguageFromChinToEng ? "MCDiscordChat is up to date!" : "当前版本已经是最新版本！") + "**").queue();
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
				  Main.config.textsEN.coloredText,
				  Main.config.textsEN.colorlessText,
				  Main.config.textsEN.removeVanillaFormattingFromDiscord,
				  Main.config.textsEN.removeLineBreakFromDiscord);
		} else {
			Main.texts = new Texts(Main.config.textsZH.serverStarted,
				  Main.config.textsZH.serverStopped,
				  Main.config.textsZH.joinServer,
				  Main.config.textsZH.leftServer,
				  Main.config.textsZH.deathMessage,
				  Main.config.textsZH.advancementTask,
				  Main.config.textsZH.advancementChallenge,
				  Main.config.textsZH.advancementGoal,
				  Main.config.textsZH.coloredText,
				  Main.config.textsZH.colorlessText,
				  Main.config.textsZH.removeVanillaFormattingFromDiscord,
				  Main.config.textsZH.removeLineBreakFromDiscord);
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
