package top.xujiayao.mcdiscordchat.utils;

import net.dv8tion.jda.api.entities.Member;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import top.xujiayao.mcdiscordchat.Main;

import java.io.File;
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
